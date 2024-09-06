package org.mastodon.views.bvv;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

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

import bdv.cache.CacheControl;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.NavigationActions;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions.Values;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

public class Playground extends MamutView< OverlayGraphWrapper< Spot, Link >, OverlayVertexWrapper< Spot, Link >, OverlayEdgeWrapper< Spot, Link > >

{

	public Playground( final ProjectModel projectModel, final OverlayGraphWrapper< Spot, Link > viewGraph, final String[] keyConfigContexts )
	{
		super( projectModel, createViewGraph( projectModel ), keyConfigContexts );

		final SharedBigDataViewerData bdvData = projectModel.getSharedBdvData();
		final VolumeViewerOptions options = getOptions( bdvData.getOptions().values );

		final ArrayList< SourceAndConverter< ? > > sources = bdvData.getSources();
		final List< ConverterSetup > setups = bdvData.getConverterSetups().getConverterSetups( sources );
		final int numTimepoints = bdvData.getNumTimepoints();
		final CacheControl cache = bdvData.getCache();
		final String title = "Trololo";

		/* THISSSSSSSSS */
		final BigVolumeViewerMamut bvv = new BigVolumeViewerMamut(
				setups,
				sources,
				numTimepoints,
				cache,
				title,
				options,
				groupHandle );

		final VolumeViewerFrameMamut frame = bvv.getViewerFrame();
		setFrame( frame );
		final VolumeViewerPanel viewer = bvv.getViewer();

		NavigationActions.install( viewActions, viewer, bdvData.is2D() );
		viewer.getTransformEventHandler().install( viewBehaviours );

		/* THISSSSSSSSS */

		final int windowWidth = options.values.getWidth();
		final int windowHeight = options.values.getHeight();
		final AffineTransform3D resetTransform = InitializeViewerState.initTransform( windowWidth, windowHeight, false, viewer.state() );
		viewer.state().setViewerTransform( resetTransform );
		final Actions actions = new Actions( new InputTriggerConfig() );
		actions.install( frame.getKeybindings(), "additional" );
		actions.runnableAction( () -> {
			viewer.state().setViewerTransform( resetTransform );
		}, "reset transform", "R" );

		if ( !bvv.tryLoadSettings( bdvData.getProposedSettingsFile().getAbsolutePath() ) )
			InitializeViewerState.initBrightness( 0.001, 0.999, viewer.state(), viewer.getConverterSetups() );

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

	protected VolumeViewerOptions getOptions( final Values values )
	{
		final int windowWidth = 640;
		final int windowHeight = 480;
		final int renderWidth = 512;
		final int renderHeight = 512;

//		final int windowWidth = 1280;
//		final int windowHeight = 960;
//		final int renderWidth = 1280;
//		final int renderHeight = 960;

//		final int renderWidth = 3840;
//		final int renderHeight = 1600;
		final int ditherWidth = 8;
		final int numDitherSamples = 8;
		final int cacheBlockSize = 64;
		final int maxCacheSizeInMB = 4000;
		final double dCam = 2000;
		final double dClip = 1000;

		final VolumeViewerOptions options = VolumeViewerOptions.options()
				.width( windowWidth )
				.height( windowHeight )
				.renderWidth( renderWidth )
				.renderHeight( renderHeight )
				.ditherWidth( ditherWidth )
				.numDitherSamples( numDitherSamples )
				.cacheBlockSize( cacheBlockSize )
				.maxCacheSizeInMB( maxCacheSizeInMB )
				.dCam( dCam )
				.dClip( dClip )
				.appearanceManager( values.getAppearanceManager() )
				.keymapManager( values.getKeymapManager() )
				.inputTriggerConfig( values.getInputTriggerConfig() )
				.msgOverlay( values.getMsgOverlay() )
				.shareKeyPressedEvents( values.getKeyPressedManager() )
				.transformEventHandlerFactory( values.getTransformEventHandlerFactory() )
				.numSourceGroups( values.getNumSourceGroups() );
		return options;
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

					new Playground( projectModel, null, new String[] { KeyConfigContexts.BIGDATAVIEWER } );

				}
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			} );
		}
	}
}
