package org.mastodon.views.bvv.playground;


import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_LINE_LOOP;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.render.RenderData;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.util.MatrixMath;
import net.imglib2.RealPoint;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.Meshes;
import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.mesh.util.Icosahedron;

public class InstancedIcosahedronRenderer
{
	private int vao;

	private DefaultShader prog;

	private int vbo;

	private int vertexCount;

	private int ebo;

	public void init( final GL3 gl, final float[] instancePositions, final float scale )
	{
		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( Playground3D.class, "vertexShader3D.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( Playground3D.class, "fragmentShader3D.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );

		// Set up icosahedron mesh data
		final int nSubdivisions = 1;
		final Mesh core = Icosahedron.sphere( new RealPoint( 3 ), scale, nSubdivisions );
		final BufferMesh mesh = new BufferMesh( core.vertices().size(), core.triangles().size() );
		Meshes.copy( core, mesh );
		final FloatBuffer vertexBuffer = mesh.vertices().verts();
		vertexBuffer.rewind();
		final IntBuffer indexBuffer = mesh.triangles().indices();
		indexBuffer.rewind();
		vertexCount = indexBuffer.capacity();

		// Generate and bind VAO
		final int[] vaos = new int[ 1 ];
		gl.glGenVertexArrays( 1, vaos, 0 );
		vao = vaos[ 0 ];
		gl.glBindVertexArray( vao );

		// Generate and bind VBO
		final int[] vbos = new int[ 1 ];
		gl.glGenBuffers( 1, vbos, 0 );
		vbo = vbos[ 0 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, vbo );
		gl.glBufferData( GL_ARRAY_BUFFER,
				vertexBuffer.capacity() * Float.BYTES,
				vertexBuffer,
				GL_STATIC_DRAW );

		// Set up vertex attribute pointer
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );

		// Generate and bind EBO
		final int[] ebos = new int[ 1 ];
		gl.glGenBuffers( 1, ebos, 0 );
		ebo = ebos[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER,
				indexBuffer.capacity() * Integer.BYTES,
				indexBuffer,
				GL_STATIC_DRAW );

		// Unbind VAO
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

		gl.glBindVertexArray( vao );

		// Render filled triangles
		prog.getUniform1i( "renderMode" ).set( 0 );
		prog.setUniforms( context );
		gl.glDrawElements( GL_TRIANGLES, vertexCount, GL_UNSIGNED_INT, 0 );
		
		// Render edges & points
		prog.getUniform1i( "renderMode" ).set( 1 );
		gl.glLineWidth( 2.0f );
		prog.setUniforms( context );
		gl.glDrawElements( GL_LINE_LOOP, vertexCount, GL_UNSIGNED_INT, 0 );
		gl.glPointSize( 5f );
		gl.glDrawElements( GL.GL_POINTS, vertexCount, GL_UNSIGNED_INT, 0 );

		gl.glBindVertexArray( 0 );
	}

	public void cleanup( final GL3 gl )
	{
		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
		gl.glDeleteBuffers( 1, new int[] { vbo }, 0 );
		gl.glDeleteBuffers( 1, new int[] { ebo }, 0 );
	}
}
