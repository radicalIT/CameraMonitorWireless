attribute vec4 vPosition;
attribute vec4 vTexCoord;

varying vec2 texCoord;

uniform mat4 uMVPMatrix; // <-- To jest kluczowe!

void main() {
    gl_Position = vPosition;

    // Mnożymy wektor tekstury przez macierz.
    // .xy na końcu jest ważne, bo vTexCoord to vec4, a texCoord to vec2
    texCoord = (uMVPMatrix * vTexCoord).xy;
}