package org.mastodon.views.bvv.playground;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINE_LOOP;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;

import java.nio.FloatBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL3;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.render.RenderData;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.util.MatrixMath;

public class InstancedIcosahedronRenderer
{
	private int vao;

	private int vboMesh;

	private int vboInstances;

	private int numInstances;

	private final float scale;

	private DefaultShader prog;

	public InstancedIcosahedronRenderer( final float scale )
	{
		this.scale = scale;
	}

	public void init( final GL3 gl, final float[] instancePositions )
	{
		// Rendering options
//		gl.glEnable( GL_LINE_SMOOTH );
//		gl.glEnable( GL_BLEND );
//		gl.glBlendFunc( GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA );

		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( Playground3D.class, "vertexShader3D.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( Playground3D.class, "fragmentShader3D.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );

		// Generate VAO
		final int[] vaos = new int[ 1 ];
		gl.glGenVertexArrays( 1, vaos, 0 );
		vao = vaos[ 0 ];
		gl.glBindVertexArray( vao );

		// Set up icosahedron mesh data
		final FloatBuffer icosahedronBuffer = Icosahedron.createIcosahedronBuffer( scale );
		final int[] vbos = new int[ 2 ];
		gl.glGenBuffers( 2, vbos, 0 );
		vboMesh = vbos[ 0 ];
		vboInstances = vbos[ 1 ];

		// Load mesh data
		gl.glBindBuffer( GL_ARRAY_BUFFER, vboMesh );
		gl.glBufferData( GL_ARRAY_BUFFER, icosahedronBuffer.capacity() * 4, icosahedronBuffer, GL_STATIC_DRAW );

		// Set up mesh vertex attribute
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 0, 0 );
		gl.glEnableVertexAttribArray( 0 );

		// Load instance data
		numInstances = instancePositions.length / 3; // Assuming 3 floats per
														// position
		final FloatBuffer instanceBuffer = Buffers.newDirectFloatBuffer( instancePositions );
		gl.glBindBuffer( GL_ARRAY_BUFFER, vboInstances );
		gl.glBufferData( GL_ARRAY_BUFFER, instanceBuffer.capacity() * 4, instanceBuffer, GL_STATIC_DRAW );

		// Set up instance position attribute
		gl.glVertexAttribPointer( 1, 3, GL_FLOAT, false, 0, 0 );
		gl.glEnableVertexAttribArray( 1 );
		gl.glVertexAttribDivisor( 1, 1 ); // This makes it per-instance

		gl.glBindBuffer( GL_ARRAY_BUFFER, 0 );
		gl.glBindVertexArray( 0 );
	}

	private final Matrix4f pvm = new Matrix4f();

	private final Matrix4f view = new Matrix4f();

	private final Matrix4f tmpVm = new Matrix4f();

	private final Matrix4f tmpItvm = new Matrix4f();

	private final Matrix3f tmpItvm3 = new Matrix3f();

	public void render( final GL3 gl, final RenderData data )
	{
		// Get current view matrices.
		pvm.set( data.getPv() );
		MatrixMath.affine( data.getRenderTransformWorldToScreen(), view );
		final Matrix4f vm = MatrixMath.screen( data.getDCam(), data.getScreenWidth(), data.getScreenHeight(), tmpVm ).mul( view );
		final Matrix4f itvm = vm.invert( tmpItvm ).transpose();

		// Pass transform matrices.
		final JoglGpuContext context = JoglGpuContext.get( gl );
		prog.use( context );
		prog.getUniformMatrix4f( "pvm" ).set( pvm );
		prog.getUniformMatrix4f( "vm" ).set( vm );
		prog.getUniformMatrix3f( "itvm" ).set( itvm.get3x3( tmpItvm3 ) );

		// Render filled triangles
		prog.getUniform1i( "renderMode" ).set( 0 );
		prog.setUniforms( context );

		gl.glBindVertexArray( vao );
		gl.glDrawArraysInstanced( GL_TRIANGLES, 0, 60, numInstances );

		// Render edges
		prog.getUniform1i( "renderMode" ).set( 1 );
		gl.glLineWidth( 2.0f );
		prog.setUniforms( context );

		gl.glDrawArraysInstanced( GL_LINE_LOOP, 0, 60, numInstances );
		gl.glBindVertexArray( 0 );
	}

	public void cleanup( final GL3 gl )
	{
		gl.glDeleteBuffers( 2, new int[] { vboMesh, vboInstances }, 0 );
		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
	}
}
