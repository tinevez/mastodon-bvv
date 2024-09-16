package org.mastodon.views.bvv.playground;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_DYNAMIC_DRAW;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.List;

import javax.swing.JFrame;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

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
import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.render.RenderData;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.util.MatrixMath;

public class Playground implements RenderScene
{

	private int vao;

	private int vbo;

	private boolean initialized = false;

	private DefaultShader prog;

	@Override
	public void render( final GL3 gl, final RenderData data )
	{
		if ( !initialized )
			init( gl );

		display( gl, data );
	}

	private void init( final GL3 gl )
	{
		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( Playground.class, "vertexShader.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( Playground.class, "fragmentShader.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );

		// Generate and bind a Vertex Array Object
		final int[] vaoArray = new int[ 1 ];
		gl.glGenVertexArrays( 1, vaoArray, 0 );
		vao = vaoArray[ 0 ];
		gl.glBindVertexArray( vao );

		// Generate and bind a Vertex Buffer Object
		final int[] vboArray = new int[ 1 ];
		gl.glGenBuffers( 1, vboArray, 0 );
		vbo = vboArray[ 0 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );

		// Set the vertex attribute pointers
		gl.glVertexAttribPointer( 0, 2, GL_FLOAT, false, 2 * 4, 0 );
		gl.glEnableVertexAttribArray( 0 );

		// Unbind the VAO and VBO
		gl.glBindBuffer( GL_ARRAY_BUFFER, 0 );
		gl.glBindVertexArray( 0 );

		initialized = true;
	}

	private void dispose( final GL3 gl )
	{
		// TODO how and when to call this?
		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
		gl.glDeleteBuffers( 1, new int[] { vbo }, 0 );
	}

	private void display( final GL3 gl, final RenderData data )
	{
		// Get current view matrices.
		final Matrix4f pvm = new Matrix4f( data.getPv() );
		final Matrix4f view = MatrixMath.affine( data.getRenderTransformWorldToScreen(), new Matrix4f() );
		final Matrix4f vm = MatrixMath.screen( data.getDCam(), data.getScreenWidth(), data.getScreenHeight(), new Matrix4f() ).mul( view );

		final JoglGpuContext context = JoglGpuContext.get( gl );
		final Matrix4f itvm = vm.invert( new Matrix4f() ).transpose();

		// Pass transform matrices.
		prog.getUniformMatrix4f( "pvm" ).set( pvm );
		prog.getUniformMatrix4f( "vm" ).set( vm );
		prog.getUniformMatrix3f( "itvm" ).set( itvm.get3x3( new Matrix3f() ) );

		// Use our shader program.
		prog.setUniforms( context );
		prog.use( context );

		// 2D coordinates.
		final float[] vertices = {
				100f, 100f,
				400f, 100f,
				400f, 400f
		};

		// Update the buffer data.
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL_ARRAY_BUFFER, vertices.length * 4, FloatBuffer.wrap( vertices ), GL_DYNAMIC_DRAW );

		// Bind the VAO.
		gl.glBindVertexArray( vao );

		// Draw the triangle.
		gl.glDrawArrays( GL_TRIANGLES, 0, 3 );

		// Unbind the VAO.
		gl.glBindVertexArray( 0 );
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

		final Playground overlay = new Playground();
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
				.is2D( true )
				.values;
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
