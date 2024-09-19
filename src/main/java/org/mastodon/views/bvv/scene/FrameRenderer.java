package org.mastodon.views.bvv.scene;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_DYNAMIC_DRAW;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_STATIC_DRAW;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;
import com.jogamp.opengl.util.GLBuffers;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.render.RenderData;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.shadergen.generate.SegmentTemplate;
import bvv.core.util.MatrixMath;
import gnu.trove.map.hash.TIntIntHashMap;
import net.imglib2.RealPoint;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.Meshes;
import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.mesh.util.Icosahedron;

/**
 * Renders all the vertices of one frame at ellispoids in OpenGL.
 */
public class FrameRenderer< V extends OverlayVertex< V, ? > >
{

	private final DefaultShader prog;

	private TIntIntHashMap idMap;

	private FloatBuffer matrixBuffer;

	private FloatBuffer translationBuffer;

	private FloatBuffer colorBuffer;

	private int vao;

	private int verticesVBO;

	private int indicesEBO;

	private int shapeVBO;

	private int instanceCount;

	private int translationVBO;

	private int colorVBO;

	public FrameRenderer()
	{
		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( FrameRenderer.class, "vertexShader3D.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( FrameRenderer.class, "fragmentShader3D.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );
	}

	void rebuild( final SpatialIndex< V > si, final Lock lock, final V ref )
	{
		final ModelDataCreator< V > creator = new ModelDataCreator<>();
		lock.lock();
		try
		{
			this.instanceCount = si.size();

			// Model matrix buffer (4x4)
			this.matrixBuffer = GLBuffers.newDirectFloatBuffer( 16 * instanceCount );
			final Matrix4f modelMatrix = new Matrix4f();

			// Translation buffer (3x1)
			this.translationBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
			final Vector3f pos = new Vector3f();
			
			// Color buffer (3x1)
			this.colorBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
			final Vector3f color = new Vector3f();

			// Map of spot id -> instance index.
			this.idMap = new TIntIntHashMap( instanceCount, 0.5f, -1, -1 );

			// Feed the buffers.
			final Iterator< V > it = si.iterator();
			for ( int i = 0; i < instanceCount; i++ )
			{
				final V v = it.next();

				// Index map.
				final int id = v.getInternalPoolIndex();
				idMap.put( id, i );

				// Model matrix for covariance.
				creator.inputShapeMatrix( v, modelMatrix );
				modelMatrix.get( i * 16, matrixBuffer );

				// X, Y, Z translation.
				creator.inputPositionVector( v, pos );
				pos.get( i * 3, translationBuffer );

				// Instance color.
				creator.inputColorVector( v, color );
				color.get( i * 3, colorBuffer );
			}
			
			// Prepare lazy initializer.
		}
		finally
		{
			lock.unlock();
		}
	}

	/*
	 * OpenGL methods.
	 */

	private final Matrix4f pvm = new Matrix4f();
	
	private final Matrix4f view = new Matrix4f();

	private final Matrix4f vm = new Matrix4f();

	private final Matrix4f itvm = new Matrix4f();

	private final Matrix3f itvm33 = new Matrix3f();

	void render( final GL3 gl, final RenderData data )
	{
		if ( matrixBuffer != null )
		{
			// We created the data, but need to pass it to the GPU.
			init( gl );
		}

		// Get current view matrices.
		pvm.set( data.getPv() );
		view.identity();
		MatrixMath.affine( data.getRenderTransformWorldToScreen(), view );
		MatrixMath.screen( data.getDCam(), data.getScreenWidth(), data.getScreenHeight(), vm ).mul( view );
		vm.invert( itvm ).transpose();
		itvm.get3x3( itvm33 );

		// Pass transform matrices.
		final JoglGpuContext context = JoglGpuContext.get( gl );
		prog.use( context );
		prog.getUniformMatrix4f( "pvm" ).set( pvm );
		prog.getUniformMatrix4f( "vm" ).set( vm );
		prog.getUniformMatrix3f( "itvm" ).set( itvm33 );
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

	private void init( final GL3 gl )
	{
		if ( matrixBuffer == null || translationBuffer == null | colorBuffer == null )
			throw new IllegalStateException( "The buffers containing vertex data have not been built." );

		// Generate and bind VAO
		final int[] vaos = new int[ 1 ];
		gl.glGenVertexArrays( 1, vaos, 0 );
		vao = vaos[ 0 ];
		gl.glBindVertexArray( vao );

		// Generate VBO names for all.
		final int[] vbos = new int[ 5 ];
		gl.glGenBuffers( 5, vbos, 0 );

		/*
		 * Bind VBO for vertex data attributes.
		 */
		verticesVBO = vbos[ 0 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, verticesVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				vertexBuffer.capacity() * Float.BYTES,
				vertexBuffer,
				GL_STATIC_DRAW );
		// Set up vertex attribute pointer -> layout = 0
		gl.glVertexAttribPointer( 0, 3, GL_FLOAT, false, 3 * Float.BYTES, 0 );
		gl.glEnableVertexAttribArray( 0 );

		/*
		 * Bind EBO for triangle indices.
		 */
		indicesEBO = vbos[ 1 ];
		gl.glBindBuffer( GL_ELEMENT_ARRAY_BUFFER, indicesEBO );
		gl.glBufferData( GL_ELEMENT_ARRAY_BUFFER,
				indexBuffer.capacity() * Integer.BYTES,
				indexBuffer,
				GL_STATIC_DRAW );

		/*
		 * Bind instance VBO for model matrices.
		 */
		shapeVBO = vbos[ 2 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, shapeVBO );
		gl.glBufferData( GL.GL_ARRAY_BUFFER,
				matrixBuffer.capacity() * Float.BYTES,
				matrixBuffer,
				GL.GL_STATIC_DRAW );
		// Set up instance attribute pointers -> layout = 1 to 4
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

		/*
		 * Bind instance VBO for translation vectors.
		 */
		translationVBO = vbos[ 3 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, translationVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				translationBuffer.capacity() * Float.BYTES,
				translationBuffer,
				GL_DYNAMIC_DRAW );
		// Set up instance attribute pointer for translation vectors -> layout = 5
		gl.glEnableVertexAttribArray( 5 );
		gl.glVertexAttribPointer( 5,
				3,
				GL_FLOAT,
				false,
				3 * Float.BYTES,
				0 );
		gl.glVertexAttribDivisor( 5, 1 );

		/*
		 * Bind instance VBO for object colors.
		 */
		colorVBO = vbos[ 4 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, colorVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				colorBuffer.capacity() * Float.BYTES,
				colorBuffer,
				GL_STATIC_DRAW );
		// Set up instance attribute pointer for color vectors -> layout = 6
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

		/*
		 * After transfer, clear the direct buffers.
		 */
		matrixBuffer = null;
		translationBuffer = null;
		colorBuffer = null;
	}

	private void cleanup( final GL3 gl )
	{
		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
		gl.glDeleteBuffers( 5, new int[] {
				verticesVBO,
				indicesEBO,
				shapeVBO,
				translationVBO,
				colorVBO }, 0 );
	}

	/*
	 * Static inner classes.
	 */

	private static class ModelDataCreator< V extends OverlayVertex< V, ? > >
	{

		final JamaEigenvalueDecomposition eig3 = new JamaEigenvalueDecomposition( 3 );

		final double[] radii = new double[ 3 ];

		final double[][] S = new double[ 3 ][ 3 ];

		final Matrix4f scaling = new Matrix4f();

		final Matrix3f rotation = new Matrix3f();

		private void inputShapeMatrix( final V v, final Matrix4f modelMatrix )
		{
			v.getCovariance( S );

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

			modelMatrix.set( rotation );
			modelMatrix.mul( scaling );
		}

		public void inputColorVector( final V v, final Vector3f holder )
		{
			// TODO Go beyond the yellow color.
			holder.x = 0.9f;
			holder.y = 0.8f;
			holder.z = 0.1f;

		}

		public void inputPositionVector( final V v, final Vector3f holder )
		{
			holder.x = v.getFloatPosition( 0 );
			holder.y = v.getFloatPosition( 1 );
			holder.z = v.getFloatPosition( 2 );
		}
	}

	/*
	 * Set up icosahedron mesh data.
	 */

	private static final int nSubdivisions = 2;

	private static final FloatBuffer vertexBuffer;

	private static final IntBuffer indexBuffer;

	private static final int vertexCount;

	static
	{
		final Mesh core = Icosahedron.sphere( new RealPoint( 3 ), 1., nSubdivisions );
		final BufferMesh mesh = new BufferMesh( core.vertices().size(), core.triangles().size() );
		Meshes.copy( core, mesh );
		vertexBuffer = mesh.vertices().verts();
		vertexBuffer.rewind();
		indexBuffer = mesh.triangles().indices();
		indexBuffer.rewind();
		vertexCount = indexBuffer.capacity();
	}
}
