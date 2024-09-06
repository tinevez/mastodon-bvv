package org.mastodon.views.bvv;

import java.awt.Graphics;
import java.util.Map;

import org.joml.Matrix4f;
import org.mastodon.collection.RefMaps;
import org.mastodon.kdtree.ClipConvexPolytope;
import org.mastodon.model.FocusModel;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bdv.overlay.OverlayEdge;
import org.mastodon.views.bdv.overlay.OverlayGraph;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.RenderSettings;
import org.mastodon.views.bdv.overlay.Visibilities;
import org.mastodon.views.bdv.overlay.Visibilities.VisibilityMode;
import org.mastodon.views.bdv.overlay.util.BdvRendererUtil;

import com.jogamp.opengl.GL3;

import bdv.util.Affine3DHelpers;
import bdv.viewer.OverlayRenderer;
import bvv.core.VolumeViewerPanel.RenderScene;
import bvv.core.render.RenderData;
import bvv.core.util.MatrixMath;
import net.imglib2.algorithm.kdtree.ConvexPolytope;
import net.imglib2.realtransform.AffineTransform3D;

public class OverlayGraphBvvRenderer< V extends OverlayVertex< V, E >, E extends OverlayEdge< E, V > > implements RenderScene, OverlayRenderer
{

	private final OverlayGraph< V, E > graph;

	private final HighlightModel< V, E > highlight;

	private final FocusModel< V > focus;

	private final SelectionModel< V, E > selection;

	private final GraphColorGenerator< V, E > coloring;

	private final SpatioTemporalIndex< V > index;

	private RenderSettings settings;

	private final Visibilities< V, E > visibilities;

	private final Map< V, StupidMesh > meshMap;

	private int width;

	private int height;

	public OverlayGraphBvvRenderer( final OverlayGraph< V, E > graph,
			final HighlightModel< V, E > highlight,
			final FocusModel< V > focus,
			final SelectionModel< V, E > selection,
			final GraphColorGenerator< V, E > coloring )
	{
		this.graph = graph;
		this.highlight = highlight;
		this.focus = focus;
		this.selection = selection;
		this.coloring = coloring;
		this.visibilities = new Visibilities<>( graph, selection, focus, graph.getLock() );
		this.meshMap = RefMaps.createRefObjectMap( graph.vertices() );
		this.index = graph.getIndex();
		setRenderSettings( RenderSettings.defaultStyle() );
	}

	@Override
	public void render( final GL3 gl, final RenderData data )
	{
		if ( visibilities.getMode() == VisibilityMode.NONE )
			return;
		if ( !settings.getDrawSpots() )
			return;

		final Matrix4f pvm = new Matrix4f( data.getPv() );
		final Matrix4f view = MatrixMath.affine( data.getRenderTransformWorldToScreen(), new Matrix4f() );
		final Matrix4f vm = MatrixMath.screen( data.getDCam(), data.getScreenWidth(), data.getScreenHeight(), new Matrix4f() ).mul( view );

		final int t = data.getTimepoint();
		graph.getLock().readLock().lock();
		index.readLock().lock();
		
		final V ref1 = graph.vertexRef();
		final V ref2 = graph.vertexRef();
		try
		{
			final AffineTransform3D transform = data.getRenderTransformWorldToScreen();
			final ClipConvexPolytope< V > ccp = index.getSpatialIndex( t ).getClipConvexPolytope();
			final ConvexPolytope cropPolytopeGlobal = getVisiblePolytopeGlobal( transform, t );
			ccp.clip( cropPolytopeGlobal );

//			System.out.println(); // DEBUG
//			for ( final Iterator< V > iterator = ccp.getInsideValues().iterator(); iterator.hasNext(); )
//				System.out.print( iterator.next().getLabel() + ", " ); // DEBUG
			
			final V highlighted = highlight.getHighlightedVertex( ref1 );
			final V focused = focus.getFocusedVertex( ref2 );
			ccp.getInsideValues()
					.forEach( s -> {
						final int color = coloring.color( s );
						final boolean isHighlighted = s.equals( highlighted );
						final boolean isFocused = s.equals( focused );
						final StupidMesh mesh = meshMap.computeIfAbsent( s, BVVUtils::icosahedron );
						mesh.setColor( color );
						if ( isFocused || isHighlighted )
							mesh.scale( 1.2 );
						else
							mesh.resetScale();

						mesh.draw( gl, pvm, vm, selection.isSelected( s ) );
					} );
		}
		finally
		{
			graph.releaseRef( ref1 );
			graph.releaseRef( ref2 );
			
			graph.getLock().readLock().unlock();
			index.readLock().unlock();
		}
	}

	/**
	 * Get the {@link ConvexPolytope} bounding the visible region of global
	 * space, extended by a large enough border to ensure that it contains the
	 * center of every ellipsoid that intersects the visible volume.
	 *
	 * @param transform
	 *            the current view transform.
	 * @param timepoint
	 *            the current timepoint.
	 * @return a convex polytope object.
	 */
	protected ConvexPolytope getVisiblePolytopeGlobal(
			final AffineTransform3D transform,
			final int timepoint )
	{
		return getOverlappingPolytopeGlobal( 0, width, 0, height, transform, timepoint );
	}

	/**
	 * Get the {@link ConvexPolytope} around the specified viewer coordinate
	 * range that is large enough border to ensure that it contains center of
	 * every ellipsoid touching the specified coordinate range.
	 *
	 * @param xMin
	 *            minimum X position on the z=0 plane in viewer coordinates.
	 * @param xMax
	 *            maximum X position on the z=0 plane in viewer coordinates.
	 * @param yMin
	 *            minimum Y position on the z=0 plane in viewer coordinates.
	 * @param yMax
	 *            maximum Y position on the z=0 plane in viewer coordinates.
	 * @param transform
	 * @param timepoint
	 * @return
	 */
	private ConvexPolytope getOverlappingPolytopeGlobal(
			final double xMin,
			final double xMax,
			final double yMin,
			final double yMax,
			final AffineTransform3D transform,
			final int timepoint )
	{
		final double maxDepth = getMaxDepth( transform );
		final double globalToViewerScale = Affine3DHelpers.extractScale( transform, 0 );
		final double border = globalToViewerScale * Math.sqrt( graph.getMaxBoundingSphereRadiusSquared( timepoint ) );
		return BdvRendererUtil.getPolytopeGlobal( transform,
				xMin - border, xMax + border,
				yMin - border, yMax + border,
				-maxDepth - border, maxDepth + border );
	}

	/**
	 * Get the maximum distance from view plane up to which to draw spots,
	 * measured in view coordinates (pixel widths).
	 *
	 * @param transform
	 *            may be needed to convert global {@code focusLimit} to view
	 *            coordinates.
	 *
	 * @return maximum distance from view plane up to which to draw spots.
	 */
	protected double getMaxDepth( final AffineTransform3D transform )
	{
		final double focusLimit = settings.getFocusLimit();
		return settings.getFocusLimitViewRelative()
				? focusLimit
				: focusLimit * Affine3DHelpers.extractScale( transform, 0 );
	}

	public void setRenderSettings( final RenderSettings settings )
	{
		this.settings = settings;
	}

	public VisibilityMode nextVisibilityMode()
	{
		return visibilities.nextMode();
	}

	public Visibilities< V, E > getVisibilities()
	{
		return visibilities;
	}

	@Override
	public void drawOverlays( final Graphics g )
	{
		// Do nothing for the OpenGL panel.
	}

	@Override
	public void setCanvasSize( final int width, final int height )
	{
		this.width = width;
		this.height = height;
	}
}
