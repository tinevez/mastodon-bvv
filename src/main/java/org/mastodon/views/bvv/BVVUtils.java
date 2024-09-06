package org.mastodon.views.bvv;

import org.mastodon.views.bdv.overlay.OverlayEdge;
import org.mastodon.views.bdv.overlay.OverlayVertex;

import net.imglib2.mesh.util.Icosahedron;

public class BVVUtils
{

	public static final < V extends OverlayVertex< V, E >, E extends OverlayEdge< E, V > > StupidMesh icosahedron( final OverlayVertex< V, E > spot )
	{
		final double radius = Math.sqrt( spot.getBoundingSphereRadiusSquared() );
		return new StupidMesh( Icosahedron.sphere( spot, radius  ) );
	}
}
