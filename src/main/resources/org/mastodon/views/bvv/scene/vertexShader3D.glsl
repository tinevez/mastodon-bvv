layout (location = 0) in vec3 aPos;
layout (location = 1) in mat4 aInstanceMatrix;
layout (location = 5) in vec3 aTranslation;
layout (location = 6) in vec3 aColor;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

out vec3 fragPos;
out vec3 fragNormal;
out vec4 fragColor;

void main()
{
	vec4 worldPos = aInstanceMatrix * vec4( aPos, 1. );
	worldPos.xyz += aTranslation;
    gl_Position = pvm * worldPos;
    
    fragColor = vec4( aColor, 1. );
    
    fragPos = vec3( vm * vec4( worldPos.xyz, 1.) );

	// Calculate normals for an ellipsoid on the fly.
	
    // The normal of a unit sphere is just the position (normalized),
    // and it is already normalized.
    vec3 sphereNormal = aPos;
    
    // Transform the normal using the transpose of the inverse of 
    // the upper-left 3x3 part of aInstanceMatrix.
    mat3 normalMatrix = transpose( inverse( mat3( aInstanceMatrix ) ) );
    fragNormal = normalize( itvm * normalMatrix * sphereNormal );
}
