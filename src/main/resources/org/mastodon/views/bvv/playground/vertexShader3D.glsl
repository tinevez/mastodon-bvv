layout (location = 0) in vec3 aPos;
layout (location = 1) in mat4 aInstanceMatrix;
layout (location = 5) in vec3 aTranslation;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

uniform int renderMode; // 0 for filled, 1 for edges

out vec3 fragPos;
out vec3 fragNormal;
out vec4 fragColor;

void main()
{
	vec4 worldPos = aInstanceMatrix * vec4(aPos, 1.0);
	worldPos.xyz += aTranslation;
    gl_Position = pvm * worldPos;
    
    // Generate a color based on the instance position
    if (renderMode == 0) {
        // Generate a color based on the instance position for filled mode
    	fragColor = vec4(normalize(aPos) * 0.5 + 0.5, 1);
    } else {
        // Set color to black for edges
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
    
    // Calculate normal (this is a simplified normal for an icosahedron)
    fragNormal = normalize(aPos);
    
    fragPos = vec3( vm * vec4( aPos, 1.0) );
}
