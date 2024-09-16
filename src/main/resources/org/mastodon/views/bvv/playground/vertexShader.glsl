layout (location = 0) in vec2 aPos;
layout (location = 1) in vec2 aOffset;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

void main()
{
    gl_Position = pvm * vec4(aPos.x + aOffset.x, aPos.y + aOffset.y, 0.0, 1.0);
}