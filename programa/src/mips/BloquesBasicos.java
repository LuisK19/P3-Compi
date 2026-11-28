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
    private static final String SUFIJO_FIN_FUNCION = "#end";

    private HashSet<String> etiquetasDestino = new HashSet<>();
    private int numeroBloque = 0;
    private boolean vieneDeUnSalto = false;
    private boolean bloqueSinInstrucciones = false;

    /**
     * Prepara la identificación anotando todas las etiquetas a las que salta
     * algún goto, y el final de cada función que tiene un return.
     *
     * @param lineas todas las líneas del código intermedio
     */
    public BloquesBasicos(String[] lineas) {
        String funcionActual = "";
        for (String lineaOriginal : lineas) {
            String linea = lineaOriginal.trim();
            if (linea.startsWith("funcion#") && esEtiqueta(linea)) {
                funcionActual = linea.substring(0, linea.length() - 1);
            } else if (linea.startsWith("return ")) {
                etiquetasDestino.add(funcionActual + SUFIJO_FIN_FUNCION);
            } else if (esSalto(linea)) {
                int posicionDestino = linea.indexOf("goto ") + "goto ".length();
                etiquetasDestino.add(linea.substring(posicionDestino));
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
        boolean empiezaNuevoBloque = esLider && !bloqueSinInstrucciones;
        if (empiezaNuevoBloque) {
            numeroBloque++;
            bloqueSinInstrucciones = true;
        }
        if (!esEtiqueta(linea)) {
            bloqueSinInstrucciones = false;
        }
        vieneDeUnSalto = esSalto(linea);
        return empiezaNuevoBloque;
    }

    /** Devuelve el número del último bloque básico que empezó. */
    public int getNumeroBloque() {
        return numeroBloque;
    }

    /**
     * Indica si una línea es un salto: goto, if con goto o return, que salta
     * al final de la función.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si es un salto condicional o incondicional
     */
    private boolean esSalto(String linea) {
        return linea.startsWith("goto ") || linea.startsWith("return ")
                || (linea.startsWith("if ") && linea.contains(" goto "));
    }

    /**
     * Indica si una línea es una etiqueta a la que salta algún goto, o el
     * final de una función al que salta algún return.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si es destino de un salto
     */
    private boolean esDestino(String linea) {
        if (esEtiqueta(linea)) {
            String nombreEtiqueta = linea.substring(0, linea.length() - 1);
            return etiquetasDestino.contains(nombreEtiqueta);
        }
        return etiquetasDestino.contains(linea);
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
