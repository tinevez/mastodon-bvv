package org.mastodon.views.bvv;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mastodon.model.FocusModel;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.spatial.SpatioTemporalIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bdv.overlay.OverlayEdge;
import org.mastodon.views.bdv.overlay.OverlayGraph;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.RenderSettings;
import org.mastodon.views.bdv.overlay.Visibilities;
import org.mastodon.views.bdv.overlay.Visibilities.VisibilityMode;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;
import org.mastodon.views.bvv.playground.InstancedIcosahedronRenderer;

import com.jogamp.opengl.GL3;

import bvv.core.VolumeViewerPanel.RenderScene;
import bvv.core.render.RenderData;

public class OverlayGraphBvvRenderer< V extends OverlayVertex< V, E >, E extends OverlayEdge< E, V > > implements RenderScene
{

	private final OverlayGraph< V, E > graph;

	private final HighlightModel< V, E > highlight;

	private final SelectionModel< V, E > selection;

	private final GraphColorGenerator< V, E > coloring;

	private final SpatioTemporalIndex< V > index;

	private RenderSettings settings;

	private final Visibilities< V, E > visibilities;

	private final Map< Integer, InstancedIcosahedronRenderer > renderers = new HashMap<>();

	public OverlayGraphBvvRenderer( final OverlayGraph< V, E > graph,
			final HighlightModel< V, E > highlight,
			final FocusModel< V > focus,
			final SelectionModel< V, E > selection,
			final GraphColorGenerator< V, E > coloring )
	{
		this.graph = graph;
		this.highlight = highlight;
		this.selection = selection;
		this.coloring = coloring;
		this.visibilities = new Visibilities<>( graph, selection, focus, graph.getLock() );
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

		final int defaultColor = settings.getColorSpot();
		final int t = data.getTimepoint();
		final InstancedIcosahedronRenderer renderer = renderers.computeIfAbsent( t, tp -> createRenderer( tp, gl ) );
		renderer.render( gl, data );
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

	private InstancedIcosahedronRenderer createRenderer( final int t, final GL3 gl )
	{
		final ModelMatrixCreator creator = new ModelMatrixCreator();

		index.readLock().lock();
		try
		{

			final SpatialIndex< V > id = index.getSpatialIndex( t );
			final int nSpots = id.size();

			final Matrix4f[] modelMatrices = new Matrix4f[ nSpots ];
			final Vector3f[] translations = new Vector3f[ nSpots ];

			final Iterator< V > it = id.iterator();
			for ( int i = 0; i < modelMatrices.length; i++ )
			{
				final V spot = it.next();
				final Matrix4f modelMatrix = creator.createShapeMatrix( spot );
				final Vector3f pos = creator.createPositionMatrix( spot );

				modelMatrices[ i ] = modelMatrix;
				translations[ i ] = pos;
			}

			final InstancedIcosahedronRenderer renderer = new InstancedIcosahedronRenderer();
			renderer.init( gl, modelMatrices, translations );
			return renderer;
		}
		finally
		{
			index.readLock().unlock();
		}
	}

	private class ModelMatrixCreator
	{

		final JamaEigenvalueDecomposition eig3 = new JamaEigenvalueDecomposition( 3 );

		final double[] radii = new double[ 3 ];

		final double[][] S = new double[ 3 ][ 3 ];

		final Matrix4f scaling = new Matrix4f();

		final Matrix3f rotation = new Matrix3f();

		private Matrix4f createShapeMatrix( final V spot )
		{
			spot.getCovariance( S );

			eig3.decomposeSymmetric( S );
			final double[] eigenvalues = eig3.getRealEigenvalues();
			for ( int d = 0; d < eigenvalues.length; d++ )
				radii[ d ] = Math.sqrt( eigenvalues[ d ] );
			final double[][] V = eig3.getV();

			// Scaling
			scaling.scaling(
					( float ) radii[ 0 ],
					( float ) radii[ 1 ],
					( float ) radii[ 2 ] );

			// Rotation
			for ( int r = 0; r < 3; r++ )
				for ( int c = 0; c < 3; c++ )
					rotation.set( c, r, ( float ) V[ c ][ r ] );

			final Matrix4f modelMatrix = new Matrix4f();
			modelMatrix.set( rotation );
			modelMatrix.mul( scaling );
			return modelMatrix;
		}

		public Vector3f createPositionMatrix( final V spot )
		{
			return new Vector3f(
					spot.getFloatPosition( 0 ),
					spot.getFloatPosition( 1 ),
					spot.getFloatPosition( 2 ) );
		}
	}
}
