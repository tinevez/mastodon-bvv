package org.mastodon.views.bvv.playground;

import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.swing.JFrame;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mastodon.mamut.ProjectModel;
import org.mastodon.mamut.io.ProjectLoader;
import org.mastodon.mamut.model.Model;
import org.mastodon.mamut.model.Spot;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;
import org.scijava.Context;

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
import mpicbg.spim.data.SpimDataException;

public class Playground3D implements RenderScene
{

	private boolean initialized = false;

	private InstancedIcosahedronRenderer renderer;

	private final Model model;

	public Playground3D( final Model model )
	{
		this.model = model;
	}

	@Override
	public void render( final GL3 gl, final RenderData data )
	{
		if ( !initialized )
			init( gl );

		display( gl, data );
	}

	private void init( final GL3 gl )
	{
		final int t = 0;

		final ModelMatrixCreator creator = new ModelMatrixCreator();

		final SpatialIndex< Spot > index = model.getSpatioTemporalIndex().getSpatialIndex( t );
		final int nSpots = index.size();

		final Matrix4f[] modelMatrices = new Matrix4f[ nSpots ];
		final Vector3f[] translations = new Vector3f[ nSpots ];

		final Iterator< Spot > it = index.iterator();

		for ( int i = 0; i < modelMatrices.length; i++ )
		{
			final Spot spot = it.next();
			final Matrix4f modelMatrix = creator.createShapeMatrix( spot );
			final Vector3f pos = creator.createPositionMatrix( spot );

			modelMatrices[ i ] = modelMatrix;
			translations[ i ] = pos;
		}

		this.renderer = new InstancedIcosahedronRenderer();
		renderer.init( gl, modelMatrices, translations );

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

	public static void main( final String[] args ) throws IOException, SpimDataException
	{
		final Context context = new Context();
		final String projectPath = "../mastodon/samples/drosophila_crop.mastodon";
		final ProjectModel projectModel = ProjectLoader.open( projectPath, context, false, false );

		// No image data.
		final List< SourceAndConverter< ? > > sources = Collections.emptyList();
		final int numTimepoints = 10;
		final CacheControl cache = new CacheControl.Dummy();
		final VolumeViewerOptions optional = getOptions();
		optional.msgOverlay( new MessageOverlayAnimator( 5000 ) );

		final VolumeViewerFrame frame = new VolumeViewerFrame( sources, numTimepoints, cache, optional );
		final VolumeViewerPanel viewer = frame.getViewerPanel();
		final Playground3D overlay = new Playground3D( projectModel.getModel() );
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

	private static class ModelMatrixCreator
	{

		final JamaEigenvalueDecomposition eig3 = new JamaEigenvalueDecomposition( 3 );

		final double[] radii = new double[ 3 ];

		final double[][] S = new double[ 3 ][ 3 ];

		final Matrix4f scaling = new Matrix4f();

		final Matrix3f rotation = new Matrix3f();

		private Matrix4f createShapeMatrix( final Spot spot )
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

		public Vector3f createPositionMatrix( final Spot spot )
		{
			return new Vector3f(
					spot.getFloatPosition( 0 ),
					spot.getFloatPosition( 1 ),
					spot.getFloatPosition( 2 ) );
		}
	}

}
