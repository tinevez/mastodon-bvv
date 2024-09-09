package org.mastodon.views.bvv;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.HashMap;
import java.util.Map;

import org.mastodon.views.bdv.overlay.OverlayEdge;
import org.mastodon.views.bdv.overlay.OverlayVertex;
import org.mastodon.views.bdv.overlay.util.JamaEigenvalueDecomposition;

import net.imglib2.RealPoint;
import net.imglib2.mesh.Vertex;
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

		private final BufferMesh unitSphere;

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
		{
			unitSphere = Icosahedron.sphere( new RealPoint( 3 ), 1. );
		}

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

			final BufferMesh out = Icosahedron.sphere( new RealPoint( 3 ), 1. );
			// new BufferMesh( nv, nt );
			final Vertices ovs = out.vertices();

			// Transform vertices.
			for ( int i = 0; i < nv; i++ )
			{
				p[ 0 ] = uvs.x( i );
				p[ 1 ] = uvs.y( i );
				p[ 2 ] = uvs.z( i );
				transformPoint( V, radii, p, tp );

//				ovs.add(
				ovs.set( i,
						tp[ 0 ] + spot.getDoublePosition( 0 ),
						tp[ 1 ] + spot.getDoublePosition( 1 ),
						tp[ 2 ] + spot.getDoublePosition( 2 ) );
			}

//			// Copy triangles.
//			copyIntBuffer( uts.indices(), out.triangles().indices() );
//			copyFloatBuffer( uts.normals(), out.triangles().normals() );

			return new StupidMesh( out );
		}

		private static void recomputeNormals( final BufferMesh src )
		{
			final Triangles triangles = src.triangles();
			final IntBuffer indices = triangles.indices();
			final FloatBuffer normals = triangles.normals();

			// Compute the triangle normals.
			indices.flip();
			normals.flip();
			while ( indices.hasRemaining() )
			{
				final int v0 = indices.get();
				final int v1 = indices.get();
				final int v2 = indices.get();

				final float v0x = src.vertices().xf( v0 );
				final float v0y = src.vertices().yf( v0 );
				final float v0z = src.vertices().zf( v0 );
				final float v1x = src.vertices().xf( v1 );
				final float v1y = src.vertices().yf( v1 );
				final float v1z = src.vertices().zf( v1 );
				final float v2x = src.vertices().xf( v2 );
				final float v2y = src.vertices().yf( v2 );
				final float v2z = src.vertices().zf( v2 );

				final float v10x = v1x - v0x;
				final float v10y = v1y - v0y;
				final float v10z = v1z - v0z;

				final float v20x = v2x - v0x;
				final float v20y = v2y - v0y;
				final float v20z = v2z - v0z;

				final float nx = v10y * v20z - v10z * v20y;
				final float ny = v10z * v20x - v10x * v20z;
				final float nz = v10x * v20y - v10y * v20x;
				final float nmag = ( float ) Math.sqrt( Math.pow( nx, 2 ) + Math.pow( ny, 2 ) + Math.pow( nz, 2 ) );

				normals.put( nx / nmag );
				normals.put( ny / nmag );
				normals.put( nz / nmag );
			}

			// Next, compute the normals per vertex based on face normals
			final HashMap< Long, float[] > vNormals = new HashMap<>();
			// Note: these are cumulative until normalized by vNbrCount

			float[] cumNormal, triNormal;
			for ( final Triangle tri : src.triangles() )
			{
				triNormal = triNormals.get( tri.index() );
				for ( final long idx : new long[] { tri.vertex0(), tri.vertex1(), tri.vertex2() } )
				{
					cumNormal = vNormals.getOrDefault( idx, new float[] { 0, 0, 0 } );
					cumNormal[ 0 ] += triNormal[ 0 ];
					cumNormal[ 1 ] += triNormal[ 1 ];
					cumNormal[ 2 ] += triNormal[ 2 ];
					vNormals.put( idx, cumNormal );
				}
			}

			// Now populate dest
			final Map< Long, Long > vIndexMap = new HashMap<>();
			float[] vNormal;
			double vNormalMag;
			// Copy the vertices, keeping track when indices change.
			for ( final Vertex v : src.vertices() )
			{
				final long srcIndex = v.index();
				vNormal = vNormals.get( v.index() );
				vNormalMag = Math.sqrt( Math.pow( vNormal[ 0 ], 2 ) + Math.pow( vNormal[ 1 ], 2 ) + Math.pow( vNormal[ 2 ], 2 ) );
				final long destIndex = dest.vertices().add( //
						v.x(), v.y(), v.z(), //
						vNormal[ 0 ] / vNormalMag, vNormal[ 1 ] / vNormalMag, vNormal[ 2 ] / vNormalMag, //
						v.u(), v.v() );
				if ( srcIndex != destIndex )
				{
					/*
					 * NB: If the destination vertex index matches the source,
					 * we skip recording the entry, to save space in the map.
					 * Later, we leave indexes unchanged which are absent from
					 * the map.
					 * 
					 * This scenario is actually quite common, because vertices
					 * are often numbered in natural order, with the first
					 * vertex having index 0, the second having index 1, etc.,
					 * although it is not guaranteed.
					 */
					vIndexMap.put( srcIndex, destIndex );
				}
			}
			// Copy the triangles, taking care to use destination indices.
			for ( final Triangle tri : src.triangles() )
			{
				final long v0src = tri.vertex0();
				final long v1src = tri.vertex1();
				final long v2src = tri.vertex2();
				final long v0 = vIndexMap.getOrDefault( v0src, v0src );
				final long v1 = vIndexMap.getOrDefault( v1src, v1src );
				final long v2 = vIndexMap.getOrDefault( v2src, v2src );
				triNormal = triNormals.get( tri.index() );
				dest.triangles().add( v0, v1, v2, triNormal[ 0 ], triNormal[ 1 ], triNormal[ 2 ] );
			}
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

	public static void main( final String[] args )
	{
		final BufferMesh mesh = new SpotMeshCreator().unitSphere;

		for ( final Triangle t : mesh.triangles() )
			System.out.println( t.nx() + ", " + t.ny() + ", " + t.nz() + ", " ); // DEBUG
	}
}
