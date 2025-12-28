precision highp float;

varying vec2 texCoord;
uniform sampler2D sTexture;    // Strumień MJPEG
uniform sampler2D sLutTexture; // Tekstura LUT

// --- PRZEŁĄCZNIKI (0 = Wyłączone, 1 = Włączone) ---
uniform int uEnableLUT;
uniform int uEnableZebra;
uniform float uZebraThreshold;
uniform int uEnablePeaking;
uniform float uPeakingThreshold; // Czułość (np. 0.05)
uniform vec3 uPeakingColor;      // Kolor (RGB)
uniform int uEnableFalseColor;

uniform float uTime;
uniform vec2 uTexelSize; // Rozmiar jednego piksela (potrzebne do Peakingu)

// Funkcja jasności
float getLuma(vec3 color) {
    return dot(color, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec4 baseColor = texture2D(sTexture, texCoord);
    vec3 finalRGB = baseColor.rgb;

    // ---------------------------------------------
    // KROK 1: FALSE COLOR (Jeśli włączony, nadpisuje wszystko)
    // ---------------------------------------------
    if (uEnableFalseColor == 1) {
        float luma = getLuma(finalRGB);
        if (luma < 0.1) finalRGB = vec3(0.5, 0.0, 0.5);       // Fiolet (Czerń)
        else if (luma < 0.25) finalRGB = vec3(0.0, 0.0, 1.0); // Niebieski
        else if (luma < 0.45) finalRGB = vec3(0.0, 1.0, 1.0); // Cyjan
        else if (luma < 0.55) finalRGB = vec3(0.0, 1.0, 0.0); // ZIELONY (Skóra)
        else if (luma < 0.8) finalRGB = vec3(1.0, 0.4, 0.7);  // Róż
        else if (luma < 0.95) finalRGB = vec3(1.0, 1.0, 0.0); // Żółty
        else finalRGB = vec3(1.0, 0.0, 0.0);                  // Czerwony (Clip)
    }
    else {
        // ---------------------------------------------
        // KROK 2: LUT (S-LOG -> REC709)
        // ---------------------------------------------
        if (uEnableLUT == 1) {
            vec4 textureColor = vec4(finalRGB, 1.0);
            float blueColor = textureColor.b * 63.0;

            vec2 quad1;
            quad1.y = floor(floor(blueColor) / 8.0);
            quad1.x = floor(blueColor) - (quad1.y * 8.0);
            vec2 quad2;
            quad2.y = floor(ceil(blueColor) / 8.0);
            quad2.x = ceil(blueColor) - (quad2.y * 8.0);

            vec2 texPos1;
            texPos1.x = (quad1.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
            texPos1.y = (quad1.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);
            vec2 texPos2;
            texPos2.x = (quad2.x * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.r);
            texPos2.y = (quad2.y * 0.125) + 0.5/512.0 + ((0.125 - 1.0/512.0) * textureColor.g);

            vec4 newColor1 = texture2D(sLutTexture, vec2(texPos1.x, 1.0 - texPos1.y));
            vec4 newColor2 = texture2D(sLutTexture, vec2(texPos2.x, 1.0 - texPos2.y));

            finalRGB = mix(newColor1, newColor2, fract(blueColor)).rgb;
        }

        // ---------------------------------------------
        // KROK 3: FOCUS PEAKING (Zmienny kolor i czułość)
        // ---------------------------------------------
        if (uEnablePeaking == 1) {
            vec3 left  = texture2D(sTexture, texCoord + vec2(-uTexelSize.x, 0.0)).rgb;
            vec3 right = texture2D(sTexture, texCoord + vec2(uTexelSize.x, 0.0)).rgb;
            vec3 up    = texture2D(sTexture, texCoord + vec2(0.0, -uTexelSize.y)).rgb;
            vec3 down  = texture2D(sTexture, texCoord + vec2(0.0, uTexelSize.y)).rgb;

            // Zgniatanie czerni (Contrast crush) - zostawiamy na sztywno, bo działa dobrze
            float contrast = 4.0;
            float l_lum = pow(getLuma(left), contrast);
            float r_lum = pow(getLuma(right), contrast);
            float u_lum = pow(getLuma(up), contrast);
            float d_lum = pow(getLuma(down), contrast);

            float edgeH = abs(l_lum - r_lum);
            float edgeV = abs(u_lum - d_lum);
            float edge = edgeH + edgeV;

            // ZMIANA: Używamy uPeakingThreshold zamiast sztywnej liczby
            // ZMIANA: Używamy uPeakingColor zamiast sztywnego zielonego
            if (edge > uPeakingThreshold) {
                finalRGB = mix(finalRGB, uPeakingColor, 0.75);
            }
        }

        // ---------------------------------------------
        // KROK 4: ZEBRA (Na wierzchu)
        // ---------------------------------------------
        if (uEnableZebra == 1) {
            float luma = getLuma(finalRGB);

            // ZMIANA: Używamy zmiennej zamiast sztywnej liczby
            if (luma > uZebraThreshold) {
                float stripes = mod((texCoord.x + texCoord.y) * 150.0 + uTime * 5.0, 2.0);
                if (stripes < 1.0) finalRGB = vec3(0.0, 0.0, 0.0);
                // else finalRGB = vec3(1.0, 0.0, 0.0);
            }
        }
    }

    gl_FragColor = vec4(finalRGB, 1.0);
}