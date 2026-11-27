package src.mips;

import java.util.HashSet;

/**
 * Identifica los bloques básicos del código intermedio con las instrucciones
 * líderes: la primera de cada sección, la que es destino de un salto y la que
 * sigue justo después de un salto.
 *
 * <p>Las líneas se revisan en orden, una por una, mientras se traducen.
 */
public class BloquesBasicos {
    private HashSet<String> destinos = new HashSet<>();
    private int numero = 0;
    private boolean vieneDeUnSalto = false;
    private boolean bloqueSinInstrucciones = false;

    /**
     * Prepara la identificación anotando todas las etiquetas a las que salta
     * algún goto.
     *
     * @param lineas todas las líneas del código intermedio
     */
    public BloquesBasicos(String[] lineas) {
        for (String linea : lineas) {
            String limpia = linea.trim();
            if (esSalto(limpia)) {
                destinos.add(limpia.substring(limpia.indexOf("goto ") + "goto ".length()));
            }
        }
    }

    /**
     * Indica que empieza una sección, así que su primera instrucción es líder.
     */
    public void iniciarSeccion() {
        vieneDeUnSalto = true;
        bloqueSinInstrucciones = false;
    }

    /**
     * Revisa la siguiente línea e indica si con ella empieza un bloque básico.
     * Si varias etiquetas seguidas son líderes, solo la primera empieza bloque.
     *
     * @param linea línea del código intermedio, sin espacios a los lados
     * @return {@code true} si la línea empieza un bloque básico
     */
    public boolean empiezaBloque(String linea) {
        boolean esLider = vieneDeUnSalto || esDestino(linea);
        boolean empieza = esLider && !bloqueSinInstrucciones;
        if (empieza) {
            numero++;
            bloqueSinInstrucciones = true;
        }
        if (!esEtiqueta(linea)) {
            bloqueSinInstrucciones = false;
        }
        vieneDeUnSalto = esSalto(linea);
        return empieza;
    }

    /** Devuelve el número del último bloque básico que empezó. */
    public int getNumero() {
        return numero;
    }

    /**
     * Indica si una línea es un salto: goto o if con goto.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si es un salto condicional o incondicional
     */
    private boolean esSalto(String linea) {
        return linea.startsWith("goto ") || (linea.startsWith("if ") && linea.contains(" goto "));
    }

    /**
     * Indica si una línea es una etiqueta a la que salta algún goto.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si es destino de un salto
     */
    private boolean esDestino(String linea) {
        return esEtiqueta(linea) && destinos.contains(linea.substring(0, linea.length() - 1));
    }

    /**
     * Indica si una línea es una etiqueta.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si termina con dos puntos
     */
    private boolean esEtiqueta(String linea) {
        return linea.endsWith(":");
    }
}
