package org.mastodon.views.bvv.playground;

import java.nio.FloatBuffer;

import com.jogamp.common.nio.Buffers;

public class Icosahedron
{
	private static final float X = 0.525731112119133606f;

	private static final float Z = 0.850650808352039932f;

	private static final float N = 0.0f;

	private static final float[] ICOSAHEDRON_VERTICES = {
			-X, N, Z, X, N, Z, -X, N, -Z, X, N, -Z,
			N, Z, X, N, Z, -X, N, -Z, X, N, -Z, -X,
			Z, X, N, -Z, X, N, Z, -X, N, -Z, -X, N
	};

	private static final int[] ICOSAHEDRON_INDICES = {
			1, 4, 0, 4, 9, 0, 4, 5, 9, 8, 5, 4,
			1, 8, 4, 1, 10, 8, 10, 3, 8, 8, 3, 5,
			3, 2, 5, 3, 7, 2, 3, 10, 7, 10, 6, 7,
			6, 11, 7, 6, 0, 11, 6, 1, 0, 10, 1, 6,
			11, 0, 9, 2, 11, 9, 5, 2, 9, 11, 2, 7
	};

	public static FloatBuffer createIcosahedronBuffer( final float scale )
	{
		final FloatBuffer vertexBuffer = Buffers.newDirectFloatBuffer( ICOSAHEDRON_INDICES.length * 3 );

		for ( final int index : ICOSAHEDRON_INDICES )
		{
			vertexBuffer.put( scale * ICOSAHEDRON_VERTICES[ index * 3 ] );
			vertexBuffer.put( scale * ICOSAHEDRON_VERTICES[ index * 3 + 1 ] );
			vertexBuffer.put( scale * ICOSAHEDRON_VERTICES[ index * 3 + 2 ] );
		}

		vertexBuffer.rewind();
		return vertexBuffer;
	}
}
