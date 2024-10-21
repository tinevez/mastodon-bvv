package org.mastodon.views.bvv.scene;

import static com.jogamp.opengl.GL.GL_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_DYNAMIC_DRAW;
import static com.jogamp.opengl.GL.GL_ELEMENT_ARRAY_BUFFER;
import static com.jogamp.opengl.GL.GL_FLOAT;
import static com.jogamp.opengl.GL.GL_POINTS;
import static com.jogamp.opengl.GL.GL_TRIANGLES;
import static com.jogamp.opengl.GL.GL_UNSIGNED_INT;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.mastodon.model.HighlightModel;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bvv.scene.OverlayModelUpdateGenerator.OverlayModelUpdate;
import org.mastodon.views.bvv.scene.OverlayModelUpdateGenerator.PositionUpdate;
import org.mastodon.views.bvv.scene.OverlayModelUpdateGenerator.ShapeUpdate;

import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL3;

import bvv.core.backend.jogl.JoglGpuContext;
import bvv.core.render.RenderData;
import bvv.core.shadergen.DefaultShader;
import bvv.core.shadergen.generate.Segment;
import bvv.core.util.MatrixMath;
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

	private int vao = 0;

	private int verticesVBO;

	private int indicesEBO;

	private int shapeVBO;

	private int translationVBO;

	private int colorVBO;

	private final OverlayModelUpdateGenerator< V > updater;

	private int numInstances;

	private boolean doRegenAll;

	private boolean doUpdateShape;

	private boolean doUpdatePosition;

	private boolean doRegenColor;

	private final ViewMatrixUpdater viewMatrixUpdater;

	private boolean doCloseRenderer = false;

	private final BvvRenderSettings settings;

	public FrameRenderer(
			final Supplier< SpatialIndex< V > > dataSupplier,
			final Lock readLock,
			final HighlightModel< V, ? > highlight,
			final SelectionModel< V, ? > selection,
			final GraphColorGenerator< V, ? > coloring,
			final BvvRenderSettings settings )
	{
		this.settings = settings;
		this.updater = new OverlayModelUpdateGenerator< V >( dataSupplier, readLock, selection, coloring, settings );
		this.doRegenAll = true;

		// Shader gen.
		final Segment shaderVp = settings.getShaderStyle().getVertexShaderSegment();
		final Segment shaderFp = settings.getShaderStyle().getFragmentShaderSegment();
		prog = new DefaultShader( shaderVp.getCode(), shaderFp.getCode() );
		viewMatrixUpdater = new ViewMatrixUpdater();
	}

	/*
	 * Update methods
	 */

	void rebuild()
	{
		doRegenAll = true;
	}

	void updateColors()
	{
		doRegenColor = true;
	}

	void updatePosition( final V v )
	{
		updater.updatePosition( v );
		doUpdatePosition = true;
	}

	void updateShape( final V v )
	{
		updater.updateShape( v );
		doUpdateShape = true;
	}

	void stop()
	{
		doCloseRenderer = true;
	}

	/*
	 * OpenGL methods.
	 */

	void render( final GL3 gl, final RenderData data )
	{
		// Is the display closing and should we close everything?
		if ( doCloseRenderer )
		{
			cleanup( gl );
			return;
		}

		// Are we initialized?
		if ( doRegenAll )
			init( gl );

		// Did the color changed?
		if ( doRegenColor )
			transferColorBuffer( gl );

		// Did the position of a vertex changed?
		if ( doUpdatePosition )
			transferPositionUdate( gl );

		// Did the shape of a vertex changed?
		if ( doUpdateShape )
			transferShapeUpdate( gl );

		// Get current view matrices and pass them to the shaders.
		viewMatrixUpdater.update( gl, data, prog );

		// Pass uniforms to shader
		prog.getUniform1f( "alpha" ).set( ( float ) settings.getTransparencyAlpha() );

		// Bind
		gl.glBindVertexArray( vao );

		gl.glEnable( GL.GL_CULL_FACE );
		gl.glCullFace( GL.GL_BACK );
		gl.glFrontFace( GL.GL_CCW );

		// Draw the actual meshes.
		if ( settings.getDrawSpotsAsSurfaces() )
			gl.glDrawElementsInstanced(
					GL_TRIANGLES,
					vertexCount,
					GL_UNSIGNED_INT,
					0,
					numInstances );
		else
			gl.glDrawElementsInstanced(
					GL_POINTS,
					vertexCount,
					GL_UNSIGNED_INT,
					0,
					numInstances );

		// Unbind
		gl.glBindVertexArray( 0 );
	}

	private void transferPositionUdate( final GL3 gl )
	{
		final PositionUpdate positionUpdate = updater.positionUpdate;
		gl.glBindBuffer( GL_ARRAY_BUFFER, translationVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				3 * positionUpdate.index * Float.BYTES,
				3 * Float.BYTES,
				positionUpdate.buffer );
		doUpdatePosition = false;
	}

	private void transferShapeUpdate( final GL3 gl )
	{
		final ShapeUpdate< V > shapeUpdate = updater.shapeUpdate;
		gl.glBindBuffer( GL_ARRAY_BUFFER, shapeVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				9 * shapeUpdate.index * Float.BYTES,
				9 * Float.BYTES,
				shapeUpdate.buffer );
		doUpdateShape = false;
	}

	private void transferColorBuffer( final GL3 gl )
	{
		final FloatBuffer colorBuffer = updater.regenColors();
		gl.glBindBuffer( GL_ARRAY_BUFFER, colorVBO );
		gl.glBufferSubData(
				GL_ARRAY_BUFFER,
				0,
				colorBuffer.capacity() * Float.BYTES,
				colorBuffer );
		doRegenColor = false;
	}

	private void init( final GL3 gl )
	{
		if ( vao != 0 )
		{
			// We have been initialized in the past and this call
			// is meant to regenerate the overlay. So we need to cleanup first.
			cleanup( gl );
		}

		// Generate update for the full model.
		final OverlayModelUpdate update = updater.regenAll();

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
				update.shapeBuffer.capacity() * Float.BYTES,
				update.shapeBuffer,
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
				update.translationBuffer.capacity() * Float.BYTES,
				update.translationBuffer,
				GL_DYNAMIC_DRAW );
		// Set up instance attribute pointer for translation vectors -> layout =
		// 4
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
				update.colorBuffer.capacity() * Float.BYTES,
				update.colorBuffer,
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

		// Store how many instances we have.
		this.numInstances = update.numInstances;

		// Reset flags.
		doRegenAll = false;
		doRegenColor = false;
		doUpdateShape = false;
		doUpdatePosition = false;
	}

	private void cleanup( final GL3 gl )
	{
		if ( vao == 0 )
			return;

		gl.glDeleteVertexArrays( 1, new int[] { vao }, 0 );
		gl.glDeleteBuffers( 5, new int[] {
				verticesVBO,
				indicesEBO,
				shapeVBO,
				translationVBO,
				colorVBO }, 0 );

		// Signal we have been cleaned.
		vao = 0;
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

	/*
	 * Static classes.
	 */

	private static class ViewMatrixUpdater
	{

		private final Matrix4f pvm = new Matrix4f();

		private final Matrix4f view = new Matrix4f();

		private final Matrix4f vm = new Matrix4f();

		private final Matrix4f itvm = new Matrix4f();

		private final Matrix3f itvm33 = new Matrix3f();

		/**
		 * Update the specified shaders with view matrices properly calculated
		 * from the specified render data. The shaders must declare and use the
		 * following uniform vec4 and vec3 matrices:
		 * <ul>
		 * <li>uniform mat4 pvm;
		 * <li>uniform mat4 vm;
		 * <li>uniform mat3 itvm;
		 * </ul>
		 * 
		 * @param gl
		 * @param data
		 * @param prog
		 */
		private void update( final GL3 gl, final RenderData data, final DefaultShader prog )
		{
			// Compute current view matrices.
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
		}
	}
}
