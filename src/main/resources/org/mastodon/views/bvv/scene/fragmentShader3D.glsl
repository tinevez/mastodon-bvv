in vec4 fragColor;
in vec3 fragPos;
in vec3 fragNormal;

out vec4 outColor;

uniform float alpha;

const vec3 lightColor1 = 0.5 * vec3(0.9, 0.9, 1);
const vec3 lightDir1 = normalize(vec3(0, -0.2, -1));
const vec3 lightColor2 = 0.5 * vec3(0.1, 0.1, 1);
const vec3 lightDir2 = normalize(vec3(1, 1, 0.5));
const vec3 ambient = vec3(0.7, 0.7, 0.7);
const float specularStrength = 1;

const float borderWidth = 0.5;
const vec3 borderColor = vec3(1, 1, 1);


vec3 phong(vec3 norm, vec3 viewDir, vec3 lightDir, vec3 lightColor, float shininess, float specularStrength)
{
	float diff = max(dot(norm, lightDir), 0.);
	vec3 diffuse = diff * lightColor;

	vec3 reflectDir = reflect(-lightDir, norm);
	float spec = pow(max(dot(viewDir, reflectDir), 0.), shininess);
	vec3 specular = specularStrength * spec * lightColor;

	return diffuse + specular;
}

void main()
{
    // Normalized view direction.
	vec3 viewDir = normalize(-fragPos);

	// Compose ellipsoid color with lights.    
	vec3 l1 = phong( fragNormal, viewDir, lightDir1, lightColor1, 32, 0.1 );
	vec3 l2 = phong( fragNormal, viewDir, lightDir2, lightColor2, 32, 0.5 );
	vec4 mixedColor = vec4( ambient + l1 + l2, 1) * fragColor;
		
	// Make a pseudo-border, based on the normal w/ respect to the view. 		
		
	// If the dot product is close to 0, we're near the edge of the ellipsoid
	float it = dot( fragNormal, viewDir );
    float edgeFactor = smoothstep( 0., borderWidth, abs(it) );
    
    // Mix between the border color and the ellipsoid color.
    vec3 finalColor = mix( borderColor, mixedColor.rgb, edgeFactor );
    
    outColor = vec4(finalColor, alpha);
}
