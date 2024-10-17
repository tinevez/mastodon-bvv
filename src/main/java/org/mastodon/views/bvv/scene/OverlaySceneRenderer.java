package org.mastodon.views.bvv.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

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

import com.jogamp.opengl.GL3;

import bvv.core.VolumeViewerPanel.RenderScene;
import bvv.core.render.RenderData;

/**
 * 3D overlay of the graph using OpenGL.
 * 
 * @param <V>
 *            the type of vertex in the overlay graph.
 * @param <E>
 *            the type of edge in the overlay graph.
 */
public class OverlaySceneRenderer< V extends OverlayVertex< V, E >, E extends OverlayEdge< E, V > >
		implements RenderScene
{

	private final OverlayGraph< V, E > graph;

	private final HighlightModel< V, E > highlight;

	private final SelectionModel< V, E > selection;

	private final GraphColorGenerator< V, E > coloring;

	private final RenderSettings settings;

	private final Visibilities< V, E > visibilities;

	private final Map< Integer, FrameRenderer< V > > renderers;

	public OverlaySceneRenderer( final OverlayGraph< V, E > graph,
			final HighlightModel< V, E > highlight,
			final FocusModel< V > focus,
			final SelectionModel< V, E > selection,
			final GraphColorGenerator< V, E > coloring,
			final RenderSettings renderSettings )
	{
		this.graph = graph;
		this.highlight = highlight;
		this.selection = selection;
		this.coloring = coloring;
		this.visibilities = new Visibilities<>( graph, selection, focus, graph.getLock() );
		this.settings = renderSettings;
		this.renderers = new HashMap<>();
	}

	@Override
	public void render( final GL3 gl, final RenderData data )
	{
		if ( visibilities.getMode() == VisibilityMode.NONE )
			return;
		if ( !settings.getDrawSpots() )
			return;

		final int t = data.getTimepoint();
		final FrameRenderer< V > renderer = renderers.computeIfAbsent( t, tp -> createRenderer( tp ) );
		renderer.render( gl, data );
	}

	private FrameRenderer< V > createRenderer( final int t )
	{
		final SpatioTemporalIndex< V > index = graph.getIndex();
		final Supplier< SpatialIndex< V > > dataSupplier = () -> index.getSpatialIndex( t );
		final FrameRenderer< V > renderer = new FrameRenderer<>(
				dataSupplier,
				index.readLock(),
				highlight,
				selection,
				coloring,
				settings );
		return renderer;
	}

	/**
	 * Signals that the color should be updated.
	 */
	public void updateColors()
	{
		for ( final Integer t : renderers.keySet() )
		{
			final FrameRenderer< V > renderer = renderers.get( t );
			if ( renderer == null )
				continue;

			renderer.updateColors();
		}
	}

	public void updatePosition( final V v )
	{
		final int t = v.getTimepoint();
		final FrameRenderer< V > renderer = renderers.get( t );
		if ( renderer == null )
			return;

		renderer.updatePosition( v );
	}

	public void updateShape( final V v )
	{
		final int t = v.getTimepoint();
		final FrameRenderer< V > renderer = renderers.get( t );
		if ( renderer == null )
			return;

		renderer.updateShape( v );
	}

	public void rebuild( final int t )
	{
		final FrameRenderer< V > renderer = renderers.get( t );
		if ( renderer != null )
			renderer.rebuild();
	}

	public void rebuild()
	{
		// Mark everything for update.
		for ( final Integer t : renderers.keySet() )
			rebuild( t );
	}

	public void stop()
	{
		for ( final Integer t : renderers.keySet() )
		{
			final FrameRenderer< V > renderer = renderers.get( t );
			if ( renderer == null )
				continue;

			renderer.stop();
		}
	}
}
