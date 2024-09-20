package org.mastodon.views.bvv.scene;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_DYNAMIC_DRAW;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.RenderSettings;
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
import net.imglib2.RealLocalizable;
import net.imglib2.RealPoint;
import net.imglib2.mesh.Mesh;
import net.imglib2.mesh.Meshes;
import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.mesh.util.Icosahedron;
import net.imglib2.type.numeric.ARGBType;

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

	private final GraphColorGenerator< V, ? > coloring;

	private final HighlightModel< V, ? > highlight;

	private final SelectionModel< V, ? > selection;

	private final RenderSettings settings;

	private boolean initialized;

	private final PositionUpdate positionUpdate;

	private final ShapeUpdate< V > shapeUpdate;

	public FrameRenderer(
			final HighlightModel< V, ? > highlight,
			final SelectionModel< V, ? > selection,
			final GraphColorGenerator< V, ? > coloring,
			final RenderSettings settings )
	{
		this.highlight = highlight;
		this.selection = selection;
		this.coloring = coloring;
		this.settings = settings;

		// Shader gen.
		final Segment shaderVp = new SegmentTemplate( FrameRenderer.class, "vertexShader3D.glsl" ).instantiate();
		final Segment shaderFp = new SegmentTemplate( FrameRenderer.class, "fragmentShader3D.glsl" ).instantiate();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );

		this.initialized = false;
		this.positionUpdate = new PositionUpdate();
		this.shapeUpdate = new ShapeUpdate<>();
	}

	/**
	 * Recreates all the buffers that will be transferred to the GPU later.
	 * 
	 * @param si
	 * @param lock
	 * @param ref
	 */
	void rebuild( final SpatialIndex< V > si, final Lock lock, final V ref )
	{
		final ModelDataCreator< V > creator = new ModelDataCreator<>();
		lock.lock();
		try
		{
			this.instanceCount = si.size();
			final int defColor = settings.getColorSpot();
			final V highlightedVertex = highlight.getHighlightedVertex( ref );

			// Model matrix buffer (4x4)
			this.matrixBuffer = GLBuffers.newDirectFloatBuffer( 9 * instanceCount );
			final Matrix3f modelMatrix = new Matrix3f();

			// Translation buffer (3x1)
			this.translationBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
			final Vector3f pos = new Vector3f();

			// Color buffer (3x1)
			this.colorBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
			final Vector3f colorVector = new Vector3f();

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
				modelMatrix.get( i * 9, matrixBuffer );

				// X, Y, Z translation.
				creator.inputPositionVector( v, pos );
				pos.get( i * 3, translationBuffer );

				// Instance color.
				getVertexColor( v, highlightedVertex, defColor, colorVector );
				colorVector.get( i * 3, colorBuffer );
			}

			// Prepare lazy initializer.
			// TODO
		}
		finally
		{
			lock.unlock();
		}
	}

	/*
	 * Update methods
	 */

	/**
	 * Recreates the color buffer that will be transferred to the GPU in the
	 * next rendering cycle. This assumes that the vertex collection did not
	 * change.
	 * 
	 * @param si
	 * @param ref
	 */
	void updateColors( final SpatialIndex< V > si, final V ref )
	{
		this.colorBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );

		final int defColor = settings.getColorSpot();
		final V highlightedVertex = highlight.getHighlightedVertex( ref );
		final Vector3f colorVector = new Vector3f();
		final Iterator< V > it = si.iterator();
		for ( int i = 0; i < instanceCount; i++ )
		{
			final V v = it.next();
			final int id = v.getInternalPoolIndex();
			final int index = idMap.get( id );

			getVertexColor( v, highlightedVertex, defColor, colorVector );
			colorVector.get( index * 3, colorBuffer );
		}
	}

	void updatePosition( final V v )
	{
		final int id = v.getInternalPoolIndex();
		final int index = idMap.get( id );
		positionUpdate.set( index, v );
	}

	void updateShape( final V v )
	{
		final int id = v.getInternalPoolIndex();
		final int index = idMap.get( id );
		shapeUpdate.set( index, v );
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
		// Are we initialized?
		if ( !initialized )
			init( gl );

		// Did the color changed?
		if ( colorBuffer != null )
			transferColorBuffer( gl );

		// Did the position of a vertex changed?
		if ( positionUpdate.todo )
			transferPositionUdate( gl );

		// Did the shape of a vertex changed?
		if ( shapeUpdate.todo )
			transferShapeUpdate( gl );

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

	private void transferPositionUdate( final GL3 gl )
	{
		gl.glBindBuffer( GL_ARRAY_BUFFER, translationVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				3 * positionUpdate.index * Float.BYTES,
				3 * Float.BYTES,
				positionUpdate.buffer );
		positionUpdate.todo = false;
	}

	private void transferShapeUpdate( final GL3 gl )
	{
		gl.glBindBuffer( GL_ARRAY_BUFFER, shapeVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				9 * shapeUpdate.index * Float.BYTES,
				9 * Float.BYTES,
				shapeUpdate.buffer );
		shapeUpdate.todo = false;
	}

	private void transferColorBuffer( final GL3 gl )
	{
		if ( colorBuffer == null )
			throw new IllegalStateException( "The color buffer has not been built." );

		gl.glBindBuffer( GL_ARRAY_BUFFER, colorVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				0,
				colorBuffer.capacity() * Float.BYTES,
				colorBuffer );
		colorBuffer = null;
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
				GL_DYNAMIC_DRAW );
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
				GL_DYNAMIC_DRAW );

		/*
		 * Bind instance VBO for model matrices.
		 */
		shapeVBO = vbos[ 2 ];
		gl.glBindBuffer( GL.GL_ARRAY_BUFFER, shapeVBO );
		gl.glBufferData( GL.GL_ARRAY_BUFFER,
				matrixBuffer.capacity() * Float.BYTES,
				matrixBuffer,
				GL.GL_DYNAMIC_DRAW );
		// Set up instance attribute pointers -> layout = 1 to 3
		final int vec3Size = 3 * Float.BYTES;
		for ( int i = 0; i < 3; i++ )
		{
			gl.glEnableVertexAttribArray( 1 + i );
			gl.glVertexAttribPointer( 1 + i,
					3,
					GL_FLOAT,
					false,
					9 * Float.BYTES,
					i * vec3Size );
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
		// Set up instance attribute pointer for translation vectors -> layout = 4
		gl.glEnableVertexAttribArray( 4 );
		gl.glVertexAttribPointer( 4,
				3,
				GL_FLOAT,
				false,
				3 * Float.BYTES,
				0 );
		gl.glVertexAttribDivisor( 4, 1 );

		/*
		 * Bind instance VBO for object colors.
		 */
		colorVBO = vbos[ 4 ];
		gl.glBindBuffer( GL_ARRAY_BUFFER, colorVBO );
		gl.glBufferData( GL_ARRAY_BUFFER,
				colorBuffer.capacity() * Float.BYTES,
				colorBuffer,
				GL_DYNAMIC_DRAW );
		// Set up instance attribute pointer for color vectors -> layout = 5
		gl.glEnableVertexAttribArray( 5 );
		gl.glVertexAttribPointer( 5,
				3,
				GL_FLOAT,
				false,
				3 * Float.BYTES,
				0 );
		gl.glVertexAttribDivisor( 5, 1 );

		// Unbind VAO
		gl.glBindVertexArray( 0 );

		/*
		 * After transfer, clear the direct buffers.
		 */
		matrixBuffer = null;
		translationBuffer = null;
		colorBuffer = null;
		initialized = true;
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
	 * Color utilities.
	 */

	private void getVertexColor( final V v, final V highlightedVertex, final int defColor, final Vector3f colorVector )
	{
		final boolean isSelected = selection.isSelected( v );
		final boolean isHighlighted = v.equals( highlightedVertex );
		final int color = coloring.color( v );
		final Color c = getColor( isSelected, isHighlighted, defColor, color );
		colorVector.x = c.getRed() / 255f;
		colorVector.y = c.getGreen() / 255f;
		colorVector.z = c.getBlue() / 255f;
	}

	private static Color getColor(
			final boolean isSelected,
			final boolean isHighlighted,
			final int defColor,
			final int color )
	{
		final int r0, g0, b0;
		if ( color == 0 )
		{
			// No coloring. Color are set by the RenderSettings.
			if ( isSelected )
			{
				final int compColor = complementaryColor( defColor );
				r0 = ( compColor >> 16 ) & 0xff;
				g0 = ( compColor >> 8 ) & 0xff;
				b0 = ( compColor ) & 0xff;
			}
			else
			{
				r0 = ( defColor >> 16 ) & 0xff;
				g0 = ( defColor >> 8 ) & 0xff;
				b0 = ( defColor ) & 0xff;
			}
		}
		else
		{
			// Use the generated color.
			r0 = isSelected ? 255 : ( ( color >> 16 ) & 0xff );
			g0 = isSelected ? 0 : ( ( color >> 8 ) & 0xff );
			b0 = isSelected ? 0 : ( color & 0xff );
		}
		final double r = r0 / 255.;
		final double g = g0 / 255.;
		final double b = b0 / 255.;
		final double a = 1.;
		return new Color( truncRGBA( r, g, b, a ), true );
	}

	private static final int complementaryColor( final int color )
	{
		return 0xff000000 | ~color;
	}

	private static int trunc255( final int i )
	{
		return Math.min( 255, Math.max( 0, i ) );
	}

	private static int truncRGBA( final int r, final int g, final int b, final int a )
	{
		return ARGBType.rgba(
				trunc255( r ),
				trunc255( g ),
				trunc255( b ),
				trunc255( a ) );
	}

	private static int truncRGBA( final double r, final double g, final double b, final double a )
	{
		return truncRGBA(
				( int ) ( 255 * r ),
				( int ) ( 255 * g ),
				( int ) ( 255 * b ),
				( int ) ( 255 * a ) );
	}

	/*
	 * Static inner classes.
	 */

	private static class PositionUpdate
	{
		private boolean todo = false;

		private int index;

		private final FloatBuffer buffer = GLBuffers.newDirectFloatBuffer( 3 );

		public void set( final int index, final RealLocalizable pos )
		{
			this.todo = true;
			this.index = index;
			buffer.clear();
			buffer
					.put( pos.getFloatPosition( 0 ) )
					.put( pos.getFloatPosition( 1 ) )
					.put( pos.getFloatPosition( 2 ) );
			buffer.rewind();
		}
	}

	private static class ShapeUpdate< V extends OverlayVertex< V, ? > >
	{

		private boolean todo = false;

		private int index;

		private final ModelDataCreator< V > creator = new ModelDataCreator<>();

		private final FloatBuffer buffer = GLBuffers.newDirectFloatBuffer( 9 );

		private final Matrix3f matrix = new Matrix3f();

		public void set( final int index, final V v )
		{
			this.todo = true;
			this.index = index;
			creator.inputShapeMatrix( v, matrix );
			buffer.clear();
			matrix.get( buffer );
			buffer.rewind();
		}
	}

	private static class ModelDataCreator< V extends OverlayVertex< V, ? > >
	{

		private final JamaEigenvalueDecomposition eig3 = new JamaEigenvalueDecomposition( 3 );

		private final double[] radii = new double[ 3 ];

		private final double[][] S = new double[ 3 ][ 3 ];

		private final Matrix3f scaling = new Matrix3f();

		private final Matrix3f rotation = new Matrix3f();

		private void inputShapeMatrix( final V v, final Matrix3f modelMatrix )
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
