package org.mastodon.views.bvv;

import static org.mastodon.app.MastodonIcons.BVV_VIEW_ICON;

import javax.swing.JFrame;

import org.mastodon.app.ui.MastodonFrameViewActions;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.io.ProjectLoader;
import org.mastodon.mamut.model.Link;
import org.mastodon.mamut.model.ModelOverlayProperties;
import org.mastodon.mamut.model.Spot;
import org.mastodon.mamut.views.MamutView;
import org.mastodon.ui.keymap.KeyConfigContexts;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.mastodon.views.bdv.overlay.wrap.OverlayEdgeWrapper;
import org.mastodon.views.bdv.overlay.wrap.OverlayGraphWrapper;
import org.mastodon.views.bdv.overlay.wrap.OverlayVertexWrapper;
import org.scijava.Context;
import org.scijava.thread.ThreadService;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.tools.InitializeViewerState;
import bdv.viewer.NavigationActions;
import bvv.core.VolumeViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

public class MamutViewBvv extends MamutView< OverlayGraphWrapper< Spot, Link >, OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > >
{

	private static int bvvName = 1;

	public MamutViewBvv(
			final ProjectModel projectModel,
			final OverlayGraphWrapper< Spot, Link > viewGraph,
			final String[] keyConfigContexts )
	{
		super( projectModel, createViewGraph( projectModel ), keyConfigContexts );
		final SharedBigDataViewerData bdvData = projectModel.getSharedBdvData();

		final String windowTitle = "BigVolumeViewer " + ( bvvName++ );
		final BigVolumeViewerMamut bvv = new BigVolumeViewerMamut(
				bdvData,
				windowTitle,
				groupHandle );

		final VolumeViewerFrameMamut frame = bvv.getViewerFrame();
		setFrame( frame );
		frame.setIconImages( BVV_VIEW_ICON );

		MastodonFrameViewActions.install( viewActions, this );
		BigVolumeViewerActionsMamut.install( viewActions, bvv );

		final VolumeViewerPanel viewer = bvv.getViewer();
		NavigationActions.install( viewActions, viewer, bdvData.is2D() );
		viewer.getTransformEventHandler().install( viewBehaviours );

		final int windowWidth = frame.getWidth();
		final int windowHeight = frame.getHeight();
		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, viewer.state() );
		viewer.state().setViewerTransform( resetTransform );
		final Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( frame.getKeybindings(), "additional" );
		actions.runnableAction( () -> {
			viewer.state().setViewerTransform( resetTransform );
			viewer.showMessage( "reset view" );
		}, "reset transform", "R" );

		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setVisible( true );
	}

	private static OverlayGraphWrapper< Spot, Link > createViewGraph(final ProjectModel appModel)
	{
		return new OverlayGraphWrapper<>(
				appModel.getModel().getGraph(),
				appModel.getModel().getGraphIdBimap(),
				appModel.getModel().getSpatioTemporalIndex(),
				appModel.getModel().getGraph().getLock(),
				new ModelOverlayProperties( appModel.getModel().getGraph(), appModel.getRadiusStats() ) );
	}

	public static void main( final String[] args )
	{

		final String projectPath = "../mastodon/samples/drosophila_crop.mastodon";
//		final String projectPath = "../mastodon/samples/MaMuT_Parhyale_small.mastodon";

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		try (Context context = new Context())
		{
			final ThreadService threadService = context.getService( ThreadService.class );
			threadService.run( () -> {
				try
				{
					final ProjectModel projectModel = ProjectLoader.open( projectPath, context, false, false );

					new MamutViewBvv( projectModel, null, new String[] { KeyConfigContexts.BIGDATAVIEWER } );

				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			} );
		}
	}
}
