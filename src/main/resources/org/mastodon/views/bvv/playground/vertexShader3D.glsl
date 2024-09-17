layout (location = 0) in vec3 aPos;
layout (location = 1) in vec3 aOffset;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

uniform int renderMode; // 0 for filled, 1 for edges

out vec3 fragPos;
out vec3 fragNormal;
out vec4 fragColor;

void main()
{
    vec3 worldPosition = aPos + aOffset;
    gl_Position = pvm * vec4(worldPosition, 1.0);
    
    // Generate a color based on the instance position
    // fragColor = vec4(normalize(aOffset) * 0.5 + 0.5, 1);
    if (renderMode == 0) {
        // Generate a color based on the instance position for filled mode
        fragColor = vec4(normalize(aOffset) * 0.5 + 0.5, 1);
    } else {
        // Set color to black for edges
        fragColor = vec4(0.0, 0.0, 0.0, 1.0);
    }
    
    // Calculate normal (this is a simplified normal for an icosahedron)
    fragNormal = normalize(aPos);
    
    fragPos = vec3( vm * vec4( worldPosition, 1.0) );
}
