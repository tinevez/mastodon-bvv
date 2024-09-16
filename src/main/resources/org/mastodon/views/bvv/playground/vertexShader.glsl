layout (location = 0) in vec2 aPos;

uniform mat4 pvm;
uniform mat4 vm;
uniform mat3 itvm;

void main()
{
    gl_Position = pvm * vec4(aPos.x, aPos.y, 0.0, 1.0);
}