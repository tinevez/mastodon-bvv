layout (location = 0) in vec3 aPos;
layout (location = 1) in mat3 aInstanceMatrix;
layout (location = 4) in vec3 aTranslation;
layout (location = 5) in vec3 aColor;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

uniform float alpha;

out vec3 fragPos;
out vec3 fragNormal;
out vec4 fragColor;

void main()
{
	// Build transform matrix by concatenating 
	// scaling+rotation matrix with translation vector.
	mat4 transformMatrix = mat4(
        vec4( aInstanceMatrix[0], 0. ),
        vec4( aInstanceMatrix[1], 0. ),
        vec4( aInstanceMatrix[2], 0. ),
        vec4( aTranslation, 1. )
    );
	
	vec4 worldPos = transformMatrix * vec4( aPos, 1. );
    gl_Position = pvm * worldPos;
    
    fragColor = vec4( aColor, alpha );
    
    fragPos = vec3( vm * vec4( worldPos.xyz, 1.) );

	// Calculate normals for an ellipsoid on the fly.
	
    // The normal of a unit sphere is just the position (normalized),
    // and it is already normalized.
    vec3 sphereNormal = aPos;
    
    // Transform the normal using the transpose of the inverse of 
    // aInstanceMatrix.
    mat3 normalMatrix = transpose( inverse( aInstanceMatrix ) );
    fragNormal = normalize( itvm * normalMatrix * sphereNormal );
}
