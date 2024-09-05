package org.mastodon.views.bvv;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JFrame;

import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.io.ProjectLoader;
import org.mastodon.views.bdv.SharedBigDataViewerData;
import org.scijava.Context;
import org.scijava.thread.ThreadService;
import org.scijava.ui.behaviour.io.InputTriggerConfig;
import org.scijava.ui.behaviour.util.Actions;

import bdv.cache.CacheControl;
import bdv.tools.InitializeViewerState;
import bdv.tools.brightness.ConverterSetup;
import bdv.viewer.SourceAndConverter;
import bvv.core.VolumeViewerFrame;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;
import net.imglib2.realtransform.AffineTransform3D;

public class Playground
{

	public void render( final SharedBigDataViewerData data )
	{}

	public static void main( final String[] args )
	{

		final String projectPath = "../mastodon/samples/drosophila_crop.mastodon";
//		final String projectPath = "../mastodon/samples/MaMuT_Parhyale_small.mastodon";

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
				.dClip( dClip );

		System.setProperty( "apple.laf.useScreenMenuBar", "true" );
		try (Context context = new Context())
		{
			final ThreadService threadService = context.getService( ThreadService.class );
			threadService.run( () -> {
				try
				{
					final ProjectModel projectModel = ProjectLoader.open( projectPath, context, false, false );
					final SharedBigDataViewerData bdvData = projectModel.getSharedBdvData();

					final ArrayList< SourceAndConverter< ? > > sources = bdvData.getSources();
					final List< ConverterSetup > setups = bdvData.getConverterSetups().getConverterSetups( sources );
					final int numTimepoints = bdvData.getNumTimepoints();
					final CacheControl cache = bdvData.getCache();
					final String title = "Trololo";

					/* THISSSSSSSSS */
					final BigVolumeViewerMamut bvv = new BigVolumeViewerMamut( setups, sources, numTimepoints, cache, title, options );
					final VolumeViewerFrame frame = bvv.getViewerFrame();
					final VolumeViewerPanel viewer = bvv.getViewer();
					/* THISSSSSSSSS */

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
				catch ( final Exception e )
				{
					e.printStackTrace();
				}
			} );
		}
	}
}
