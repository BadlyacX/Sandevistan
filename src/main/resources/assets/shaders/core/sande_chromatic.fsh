#version 150

uniform sampler2D DiffuseSampler;
uniform vec2 InSize;
uniform float Time;
uniform float Strength;
uniform vec3 Tint;

in vec2 texCoord;
out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1,311.7))) * 43758.5453123);
}

void main() {
    vec2 uv = texCoord;

    float px = 1.0 / InSize.x;
    float py = 1.0 / InSize.y;

    float t = Time * 0.8;
    float jitter = (hash(uv + t) - 0.5) * 2.0 * 0.5;
    float offs = Strength * (0.75 + 0.25 * sin(t * 6.2831)) * 1.5;

    vec2 shiftX = vec2(px * offs, 0.0);
    vec2 shiftY = vec2(0.0, py * offs * 0.4);

    vec3 col;
    col.r = texture(DiffuseSampler, uv + shiftX + jitter * shiftY).r;
    col.g = texture(DiffuseSampler, uv - shiftX * 0.6).g;
    col.b = texture(DiffuseSampler, uv + shiftY * 0.8).b;

    vec3 base = texture(DiffuseSampler, uv).rgb;
    col = mix(base, col, 0.75);

    col = mix(col, col * Tint, 0.20);

    float vign = smoothstep(0.0, 0.9, 1.0 - length(uv * 2.0 - 1.0));
    col += (1.0 - vign) * 0.05 * Strength;

    fragColor = vec4(col, 1.0);
}
