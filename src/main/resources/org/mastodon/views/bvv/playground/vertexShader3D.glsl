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
    
    // Calculate normal (this is a simplified normal for an icosahedron)
    fragNormal = itvm * normalize( aPos );
    
    fragPos = vec3( vm * vec4( worldPos.xyz, 1.) );
}
