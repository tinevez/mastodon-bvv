package org.mastodon.views.bvv.playground;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.GLBuffers;

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

	private int instanceCount;

	private int instanceVBO;

	private int translationVBO;

	private final int nSubdivisions;

	private int colorVBO;

	private Runnable cleanup;

	public InstancedIcosahedronRenderer()
	{
		this( 2 );
	}

	public InstancedIcosahedronRenderer( final int inSubdivisions )
	{
		this.nSubdivisions = inSubdivisions;
	}

	public void init( final GL3 gl,
			final Matrix4fc[] modelMatrices,
			final Vector3f[] translations,
			final Vector3f[] colors )
	{
		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( InstancedIcosahedronRenderer.class, "vertexShader3D.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( InstancedIcosahedronRenderer.class, "fragmentShader3D.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );

		// Set up icosahedron mesh data
		final Mesh core = Icosahedron.sphere( new RealPoint( 3 ), 1., nSubdivisions );
		final BufferMesh mesh = new BufferMesh( core.vertices().size(), core.triangles().size() );
		Meshes.copy( core, mesh );
		final FloatBuffer vertexBuffer = mesh.vertices().verts();
		vertexBuffer.rewind();
		final IntBuffer indexBuffer = mesh.triangles().indices();
		indexBuffer.rewind();

		vertexCount = indexBuffer.capacity();
		instanceCount = modelMatrices.length;

		// Generate and bind VAO
		final int[] vaos = new int[ 1 ];
		gl.glGenVertexArrays( 1, vaos, 0 );
		vao = vaos[ 0 ];
		gl.glBindVertexArray( vao );

		// Generate and bind VBO for vertex data
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

		// Generate and bind EBO for triangle indices
		final int[] ebos = new int[ 1 ];
		gl.glGenBuffers( 1, ebos, 0 );
		ebo = ebos[ 0 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, ebo );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER,
				indexBuffer.capacity() * Integer.BYTES,
				indexBuffer,
				GL_STATIC_DRAW );

		// Generate and bind instance VBO for model matrices
		final int[] instanceVBOs = new int[ 1 ];
		gl.glGenBuffers( 1, instanceVBOs, 0 );
		instanceVBO = instanceVBOs[ 0 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, instanceVBO );
		gl.glBufferData( GL.GL_ARRAY_BUFFER,
				instanceCount * 16 * Float.BYTES,
				null,
				GL.GL_STATIC_DRAW );

		// Fill the buffer
		final FloatBuffer matrixBuffer = GLBuffers.newDirectFloatBuffer( 16 );
		for ( int i = 0; i < instanceCount; i++ )
		{
			modelMatrices[ i ].get( matrixBuffer );
			gl.glBufferSubData( GL.GL_ARRAY_BUFFER,
					i * 16 * Float.BYTES,
					16 * Float.BYTES,
					matrixBuffer );
		}

		// Set up instance attribute pointers
		final int vec4Size = 4 * Float.BYTES;
		for ( int i = 0; i < 4; i++ )
		{
			gl.glEnableVertexAttribArray( 1 + i );
			gl.glVertexAttribPointer( 1 + i,
					4,
					GL_FLOAT,
					false,
					16 * Float.BYTES,
					i * vec4Size );
			gl.glVertexAttribDivisor( 1 + i, 1 );
		}

		// Generate and bind instance VBO for translation vectors
		final int[] translationVBOs = new int[ 1 ];
		gl.glGenBuffers( 1, translationVBOs, 0 );
		translationVBO = translationVBOs[ 0 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, translationVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				instanceCount * 3 * Float.BYTES,
				null,
				GL_STATIC_DRAW );

		// Fill the buffer
		final FloatBuffer translationBuffer = GLBuffers.newDirectFloatBuffer( 3 );
		for ( int i = 0; i < instanceCount; i++ )
		{
			translations[ i ].get( translationBuffer );
			gl.glBufferSubData( GL_ARRAY_BUFFER,
					i * 3 * Float.BYTES,
					3 * Float.BYTES,
					translationBuffer );
		}

		// Set up instance attribute pointer for translation vectors
		gl.glEnableVertexAttribArray( 5 );
		gl.glVertexAttribPointer( 5,
				3,
				GL_FLOAT,
				false,
				3 * Float.BYTES,
				0 );
		gl.glVertexAttribDivisor( 5, 1 );

		// Generate and bind instance VBO for object colors
		final int[] colorVBOs = new int[ 1 ];
		gl.glGenBuffers( 1, colorVBOs, 0 );
		colorVBO = colorVBOs[ 0 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, colorVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				instanceCount * 3 * Float.BYTES,
				null,
				GL_STATIC_DRAW );

		// Fill the buffer
		final FloatBuffer colorBuffer = GLBuffers.newDirectFloatBuffer( 3 );
		for ( int i = 0; i < instanceCount; i++ )
		{
			colors[ i ].get( colorBuffer );
			gl.glBufferSubData( GL_ARRAY_BUFFER,
					i * 3 * Float.BYTES,
					3 * Float.BYTES,
					colorBuffer );
		}

		// Set up instance attribute pointer for color vectors
		gl.glEnableVertexAttribArray( 6 );
		gl.glVertexAttribPointer( 6,
				3,
				GL_FLOAT,
				false,
				3 * Float.BYTES,
				0 );
		gl.glVertexAttribDivisor( 6, 1 );

		// Unbind VAO
		gl.glBindVertexArray( 0 );

		// Prepare cleanup when we will be invalidated.
		this.cleanup = () -> cleanup( gl );
	}

	public void render( final GL3 gl, final RenderData data )
	{
		// Get current view matrices.
		final Matrix4f pvm = new Matrix4f( data.getPv() );
		final Matrix4f view = MatrixMath.affine( data.getRenderTransformWorldToScreen(), new Matrix4f() );
		final Matrix4f vm = MatrixMath.screen( data.getDCam(), data.getScreenWidth(), data.getScreenHeight(), new Matrix4f() ).mul( view );
		final Matrix4f itvm = vm.invert( new Matrix4f() ).transpose();

		// Pass transform matrices.
		final JoglGpuContext context = JoglGpuContext.get( gl );
		prog.use( context );
		prog.getUniformMatrix4f( "pvm" ).set( pvm );
		prog.getUniformMatrix4f( "vm" ).set( vm );
		prog.getUniformMatrix3f( "itvm" ).set( itvm.get3x3( new Matrix3f() ) );
		prog.setUniforms( context );

		// Bind
		gl.glBindVertexArray( vao );

		// Draw the actual meshes.
		gl.glDrawElementsInstanced(
				GL_TRIANGLES,
				vertexCount,
				GL_UNSIGNED_INT,
				0,
				instanceCount );

		// Unbind
		gl.glBindVertexArray( 0 );
	}

	public void invalidate()
	{
		if ( cleanup != null )
			cleanup.run();
	}

	private void cleanup( final GL3 gl )
	{
		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
		gl.glDeleteBuffers( 1, new int[] { vbo }, 0 );
		gl.glDeleteBuffers( 1, new int[] { ebo }, 0 );
		gl.glDeleteBuffers( 1, new int[] { instanceVBO }, 0 );
		gl.glDeleteBuffers( 1, new int[] { translationVBO }, 0 );
		gl.glDeleteBuffers( 1, new int[] { colorVBO }, 0 );
	}
}
