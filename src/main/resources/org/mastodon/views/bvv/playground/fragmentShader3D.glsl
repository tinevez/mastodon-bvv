uniform int renderMode;

in vec4 fragColor;
in vec3 fragPos;
in vec3 fragNormal;

out vec4 outColor;

const vec3 lightColor1 = 0.5 * vec3(0.9, 0.9, 1);
const vec3 lightDir1 = normalize(vec3(0, -0.2, -1));
const vec3 lightColor2 = 0.5 * vec3(0.1, 0.1, 1);
const vec3 lightDir2 = normalize(vec3(1, 1, 0.5));
const vec3 ambient = vec3(0.7, 0.7, 0.7);
const float specularStrength = 1;


vec3 phong(vec3 norm, vec3 viewDir, vec3 lightDir, vec3 lightColor, float shininess, float specularStrength)
{
	float diff = max(dot(norm, lightDir), 0.0);
	vec3 diffuse = diff * lightColor;

	vec3 reflectDir = reflect(-lightDir, norm);
	float spec = pow(max(dot(viewDir, reflectDir), 0.0), shininess);
	vec3 specular = specularStrength * spec * lightColor;

	return diffuse + specular;
}


void main()
{
	if (renderMode == 1)
	{
		outColor = fragColor;
	}
	else
	{
	    // Normalize the interpolated normal
	    vec3 norm = normalize(fragNormal);
	    
		vec3 viewDir = normalize(-fragPos);
		vec3 l1 = phong( norm, viewDir, lightDir1, lightColor1, 32, 0.1 );
		vec3 l2 = phong( norm, viewDir, lightDir2, lightColor2, 32, 0.5 );
		
		outColor = vec4( ambient + l1 + l2, 1) * fragColor;
	}
}
