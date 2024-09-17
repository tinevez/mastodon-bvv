package org.mastodon.views.bvv.playground;

import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;

import com.jogamp.opengl.GL3;

import bdv.cache.CacheControl;
import bdv.viewer.SourceAndConverter;
import bdv.viewer.ViewerOptions;
import bdv.viewer.ViewerOptions.Values;
import bdv.viewer.animate.MessageOverlayAnimator;
import bvv.core.VolumeViewerFrame;
import bvv.core.VolumeViewerOptions;
import bvv.core.VolumeViewerPanel;
import bvv.core.VolumeViewerPanel.RenderScene;
import bvv.core.render.RenderData;

public class Playground3D implements RenderScene
{

	private boolean initialized = false;

	private InstancedIcosahedronRenderer renderer;

	@Override
	public void render( final GL3 gl, final RenderData data )
	{
		if ( !initialized )
			init( gl );

		display( gl, data );
	}

	private void init( final GL3 gl )
	{
		// Instances TODO they are not used yet.
		final float[] instancePositions = {
				200.0f, 200.0f, 0.0f,
				400.0f, 200.0f, 0.0f,
				100.0f, 200.0f, 0.0f,
				100.0f, 400.0f, 0.0f,
				100.0f, 100.0f, 0.0f
		};

		this.renderer = new InstancedIcosahedronRenderer();
		renderer.init( gl, instancePositions, 50f );

		initialized = true;
	}

	private void dispose( final GL3 gl )
	{
		renderer.cleanup( gl );
	}

	private void display( final GL3 gl, final RenderData data )
	{
		// Display
		renderer.render( gl, data );
	}

	public static void main( final String[] args )
	{
		// No image data.
		final List< SourceAndConverter< ? > > sources = Collections.emptyList();
		final int numTimepoints = 10;
		final CacheControl cache = new CacheControl.Dummy();
		final VolumeViewerOptions optional = getOptions();
		optional.msgOverlay( new MessageOverlayAnimator( 5000 ) );

		final VolumeViewerFrame frame = new VolumeViewerFrame( sources, numTimepoints, cache, optional );
		final VolumeViewerPanel viewer = frame.getViewerPanel();
		final Playground3D overlay = new Playground3D();
		viewer.setRenderScene( overlay );

		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setVisible( true );

		final MessageOverlayAnimator msgOverlay = optional.values.getMsgOverlay();
		msgOverlay.add( "Move the triangle" );
	}

	private static VolumeViewerOptions getOptions()
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

		final Values values = new ViewerOptions()
				.is2D( false ).values;
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

}
