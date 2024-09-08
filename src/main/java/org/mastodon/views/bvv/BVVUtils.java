package org.mastodon.views.bvv;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;

import org.mastodon.views.bdv.overlay.OverlayEdge;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;

import net.imglib2.RealPoint;
import net.imglib2.mesh.impl.nio.BufferMesh;
import net.imglib2.mesh.impl.nio.BufferMesh.Triangles;
import net.imglib2.mesh.impl.nio.BufferMesh.Vertices;
import net.imglib2.mesh.util.Icosahedron;

public class BVVUtils
{

	public static SpotMeshCreator meshCreator()
	{
		return new SpotMeshCreator();
	}

	public static class SpotMeshCreator
	{

		/**
		 * spot covariance in global coordinate system.
		 */
		private final double[][] S = new double[ 3 ][ 3 ];

		private final JamaEigenvalueDecomposition eig3 = new JamaEigenvalueDecomposition( 3 );

		private final double[] radii = new double[ 3 ];

		private final double[] p = new double[ 3 ];

		private final double[] tp = new double[ 3 ];

		final BufferMesh unitSphere = Icosahedron.sphere( new RealPoint( 3 ), 1. );

		private static void transformPoint(
				final double[][] eigenvectors,
				final double[] radii,
				final double[] point,
				final double[] transformedPoint )
		{
			// Step 1: Scale the point
			final double[] scaledPoint = new double[ 3 ];
			for ( int d = 0; d < 3; d++ )
				scaledPoint[ d ] = radii[ d ] * point[ d ];

			// Step 2: Rotate the scaled point
			for ( int i = 0; i < 3; i++ )
			{
				transformedPoint[ i ] = 0;
				for ( int j = 0; j < 3; j++ )
					transformedPoint[ i ] += eigenvectors[ i ][ j ] * scaledPoint[ j ];
			}
		}

		private SpotMeshCreator()
		{}

		public final < V extends OverlayVertex< V, E >, E extends OverlayEdge< E, V > > StupidMesh createMesh( final OverlayVertex< V, E > spot )
		{
			spot.getCovariance( S );
			eig3.decomposeSymmetric( S );
			final double[] eigenvalues = eig3.getRealEigenvalues();
			for ( int d = 0; d < eigenvalues.length; d++ )
				radii[ d ] = Math.sqrt( eigenvalues[ d ] );
			final double[][] V = eig3.getV();

			final Vertices uvs = unitSphere.vertices();
			final int nv = uvs.size();
			final Triangles uts = unitSphere.triangles();
			final int nt = uts.size();

			final BufferMesh out = new BufferMesh( nv, nt );
			final Vertices ovs = out.vertices();

			// Transform vertices.
			for ( int i = 0; i < nv; i++ )
			{
				p[ 0 ] = uvs.x( i );
				p[ 1 ] = uvs.y( i );
				p[ 2 ] = uvs.z( i );
				transformPoint( V, radii, p, tp );

				ovs.add(
						tp[ 0 ] + spot.getDoublePosition( 0 ),
						tp[ 1 ] + spot.getDoublePosition( 1 ),
						tp[ 2 ] + spot.getDoublePosition( 2 ) );
			}

			// Copy triangles.
			copyIntBuffer( uts.indices(), out.triangles().indices() );
			copyFloatBuffer( uts.normals(), out.triangles().normals() );

			return new StupidMesh( out );
		}

		private static void copyIntBuffer( final IntBuffer source, final IntBuffer destination )
		{
			// Store the original positions
			final int srcPos = source.position();
			final int destPos = destination.position();

			try
			{
				// Reset positions to beginning
				source.position( 0 );
				destination.position( 0 );

				// Ensure destination has enough remaining space
				if ( destination.remaining() < source.remaining() )
				{
					destination.limit( Math.min( destination.capacity(), source.remaining() ) );
				}

				// Perform the bulk copy
				destination.put( source );
			}
			finally
			{
				// Restore original positions
				source.position( srcPos );
				destination.position( destPos );
			}
		}

		private static void copyFloatBuffer( final FloatBuffer source, final FloatBuffer destination )
		{
			// Store the original positions
			final int srcPos = source.position();
			final int destPos = destination.position();

			try
			{
				// Reset positions to beginning
				source.position( 0 );
				destination.position( 0 );

				// Ensure destination has enough remaining space
				if ( destination.remaining() < source.remaining() )
				{
					destination.limit( Math.min( destination.capacity(), source.remaining() ) );
				}

				// Perform the bulk copy
				destination.put( source );
			}
			finally
			{
				// Restore original positions
				source.position( srcPos );
				destination.position( destPos );
			}
		}
	}
}
