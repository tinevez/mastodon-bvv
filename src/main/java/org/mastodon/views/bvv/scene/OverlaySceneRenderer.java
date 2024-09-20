package org.mastodon.views.bvv.scene;

import java.util.HashMap;
import java.util.Map;

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
		final SpatialIndex< V > si = index.getSpatialIndex( t );
		final V ref = graph.vertexRef();
		try
		{
			final FrameRenderer< V > renderer = new FrameRenderer<>(
					highlight,
					selection,
					coloring,
					settings );
			renderer.rebuild( si, index.readLock(), ref );
			return renderer;
		}
		finally
		{
			graph.releaseRef( ref );
		}
	}

	/**
	 * Signals that the color should be updated.
	 * 
	 * TODO Right now this iterates through ALL the vertices of the model to
	 * renew their color. The buffers are actually transferred to the GPU only
	 * when needed, but we still iterate on all the spots here. Is there a way
	 * to make this 'lazy'? This would require accessing the vertices at render
	 * time...
	 */
	public void updateColors()
	{
		final SpatioTemporalIndex< V > index = graph.getIndex();
		for ( final Integer t : renderers.keySet() )
		{
			final FrameRenderer< V > renderer = renderers.get( t );
			if ( renderer == null )
				continue;

			final SpatialIndex< V > si = index.getSpatialIndex( t );
			final V ref = graph.vertexRef();
			try
			{
				renderer.updateColors( si, ref );
			}
			finally
			{
				graph.releaseRef( ref );
			}
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
}
