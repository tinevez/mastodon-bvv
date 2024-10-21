package org.mastodon.views.bvv.scene;

import java.awt.Color;
import java.nio.FloatBuffer;
import java.util.Iterator;
import java.util.concurrent.locks.Lock;
import java.util.function.Supplier;

import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.mastodon.model.SelectionModel;
import org.mastodon.spatial.SpatialIndex;
import org.mastodon.ui.coloring.GraphColorGenerator;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;

import com.jogamp.opengl.util.GLBuffers;

import gnu.trove.map.hash.TIntIntHashMap;
import net.imglib2.RealLocalizable;
import net.imglib2.type.numeric.ARGBType;

/**
 * Represents an update of the model representation in the GPU, in a form ready
 * to be transferred.
 */
public class OverlayModelUpdateGenerator< V extends OverlayVertex< V, ? > >
{

	private final Supplier< SpatialIndex< V > > dataSupplier;

	private final ModelDataCreator< V > creator = new ModelDataCreator<>();

	private final BvvRenderSettings settings;

	private final SelectionModel< V, ? > selection;

	private final GraphColorGenerator< V, ? > coloring;

	private final Lock readLock;

	private TIntIntHashMap idMap;

	final ShapeUpdate< V > shapeUpdate;

	final PositionUpdate positionUpdate;

	public OverlayModelUpdateGenerator(
			final Supplier< SpatialIndex< V > > dataSupplier,
			final Lock readLock,
			final SelectionModel< V, ? > selection,
			final GraphColorGenerator< V, ? > coloring,
			final BvvRenderSettings settings )
	{
		this.dataSupplier = dataSupplier;
		this.readLock = readLock;
		this.selection = selection;
		this.coloring = coloring;
		this.settings = settings;
		this.shapeUpdate = new ShapeUpdate<>();
		this.positionUpdate = new PositionUpdate();
	}

	/**
	 * Recreates the color buffer for the whole model. This assumes that the
	 * vertex collection in the current frame has not changed.
	 * 
	 * @return a new {@link FloatBuffer}
	 */
	FloatBuffer regenColors()
	{
		final SpatialIndex< V > si = dataSupplier.get();
		final int size = si.size();

		final FloatBuffer colorBuffer = GLBuffers.newDirectFloatBuffer( 3 * size );

		final int defColor = settings.getColorSpot();
		final int selectionColor = settings.getSelectionColor();
		final Vector3f colorVector = new Vector3f();
		final Iterator< V > it = si.iterator();
		for ( int i = 0; i < size; i++ )
		{
			final V v = it.next();
			final int id = v.getInternalPoolIndex();
			final int index = idMap.get( id );

			getVertexColor( v, defColor, selectionColor, colorVector );
			colorVector.get( index * 3, colorBuffer );
		}
		return colorBuffer;
	}

	void updateShape( final V v )
	{
		final int index = idMap.get( v.getInternalPoolIndex() );
		shapeUpdate.set( index, v );
	}

	void updatePosition( final V v )
	{
		final int index = idMap.get( v.getInternalPoolIndex() );
		positionUpdate.set( index, v );
	}

	/**
	 * Recreates all the buffers that will be transferred to the GPU later.
	 * 
	 * @return an update representing the full model.
	 */
	OverlayModelUpdate regenAll()
	{
		final SpatialIndex< V > si = dataSupplier.get();
		final int instanceCount = si.size();
		final int defColor = settings.getColorSpot();
		final int selectionColor = settings.getSelectionColor();

		// Model matrix buffer (4x4)
		final FloatBuffer shapeBuffer = GLBuffers.newDirectFloatBuffer( 9 * instanceCount );
		final Matrix3f modelMatrix = new Matrix3f();

		// Translation buffer (3x1)
		final FloatBuffer translationBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
		final Vector3f pos = new Vector3f();

		// Color buffer (3x1)
		final FloatBuffer colorBuffer = GLBuffers.newDirectFloatBuffer( 3 * instanceCount );
		final Vector3f colorVector = new Vector3f();

		// Map of spot id -> instance index.
		this.idMap = new TIntIntHashMap( instanceCount, 0.5f, -1, -1 );

		// Feed the buffers.
		readLock.lock();
		try
		{

			final Iterator< V > it = si.iterator();
			for ( int i = 0; i < instanceCount; i++ )
			{
				final V v = it.next();

				// Index map.
				final int id = v.getInternalPoolIndex();
				idMap.put( id, i );

				// Model matrix for covariance.
				creator.inputShapeMatrix( v, modelMatrix );
				modelMatrix.get( i * 9, shapeBuffer );

				// X, Y, Z translation.
				creator.inputPositionVector( v, pos );
				pos.get( i * 3, translationBuffer );

				// Instance color.
				getVertexColor( v, defColor, selectionColor, colorVector );
				colorVector.get( i * 3, colorBuffer );
			}
		}
		finally
		{
			readLock.unlock();
		}

		return new OverlayModelUpdate( si.size(), shapeBuffer, translationBuffer, colorBuffer );
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

	static class OverlayModelUpdate
	{

		final FloatBuffer shapeBuffer;

		final FloatBuffer translationBuffer;

		final FloatBuffer colorBuffer;

		final int numInstances;

		private OverlayModelUpdate( final int numInstances, final FloatBuffer shapeBuffer, final FloatBuffer translationBuffer, final FloatBuffer colorBuffer )
		{
			this.numInstances = numInstances;
			this.shapeBuffer = shapeBuffer;
			this.translationBuffer = translationBuffer;
			this.colorBuffer = colorBuffer;
		}
	}

	/*
	 * Color utilities.
	 */

	private void getVertexColor( final V v, final int defColor, final int selectionColor, final Vector3f colorVector )
	{
		final boolean isSelected = selection.isSelected( v );
		final int color = coloring.color( v );
		final Color c = getColor( isSelected, defColor, selectionColor, color );
		colorVector.x = c.getRed() / 255f;
		colorVector.y = c.getGreen() / 255f;
		colorVector.z = c.getBlue() / 255f;
	}

	private static Color getColor(
			final boolean isSelected,
			final int defColor,
			final int selectionColor,
			final int color )
	{
		final int r0, g0, b0;
		if ( color == 0 )
		{
			// No coloring. Color are set by the RenderSettings.
			if ( isSelected )
			{
				r0 = ( selectionColor >> 16 ) & 0xff;
				g0 = ( selectionColor >> 8 ) & 0xff;
				b0 = ( selectionColor ) & 0xff;
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
	 * Static classes.
	 */

	static class PositionUpdate
	{

		int index;

		final FloatBuffer buffer = GLBuffers.newDirectFloatBuffer( 3 );

		public void set( final int index, final RealLocalizable pos )
		{
			this.index = index;
			buffer.clear();
			buffer
					.put( pos.getFloatPosition( 0 ) )
					.put( pos.getFloatPosition( 1 ) )
					.put( pos.getFloatPosition( 2 ) );
			buffer.rewind();
		}
	}

	static class ShapeUpdate< V extends OverlayVertex< V, ? > >
	{

		int index;

		final FloatBuffer buffer = GLBuffers.newDirectFloatBuffer( 9 );

		private final ModelDataCreator< V > creator = new ModelDataCreator<>();

		private final Matrix3f matrix = new Matrix3f();

		private void set( final int index, final V v )
		{
			this.index = index;
			creator.inputShapeMatrix( v, matrix );
			buffer.clear();
			matrix.get( buffer );
			buffer.rewind();
		}
	}
}
