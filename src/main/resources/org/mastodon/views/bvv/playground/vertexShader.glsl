layout (location = 0) in vec2 aPos;
layout (location = 1) in vec4 aTransform; // 2x2 matrix stored as vec4
layout (location = 2) in vec2 aOffset;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

void main()
{
	vec2 transformed = vec2(
        aTransform.x * aPos.x + aTransform.y * aPos.y,
        aTransform.z * aPos.x + aTransform.w * aPos.y
    );
    gl_Position = pvm * vec4(transformed + aOffset, 0.0, 1.0);
}