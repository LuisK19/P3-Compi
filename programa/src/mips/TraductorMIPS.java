package src.mips;

/**
 * Traduce el código intermedio de tres direcciones a código MIPS que se puede
 * correr en QtSpim.
 *
 * <p>Cada línea del código intermedio se traduce por separado. Lo que va en la
 * sección de datos (globales y textos) se junta aparte de las instrucciones y
 * al final se arma el programa completo.
 */
public class TraductorMIPS {
    private static final String FIN_PRINCIPAL = "funcion#void#principal#end";

    private StringBuilder datos = new StringBuilder();
    private StringBuilder texto = new StringBuilder();

    /**
     * Traduce un programa completo. La ejecución empieza en main.
     *
     * @param codigoIntermedio código intermedio generado por el parser, sin errores
     * @return código MIPS del programa
     */
    public String traducir(String codigoIntermedio) {
        texto.append(etiqueta("main"));
        for (String linea : codigoIntermedio.split("\n")) {
            traducirLinea(linea.trim());
        }
        return ".data\n" + datos + "\n.text\n.globl main\n" + texto;
    }

    /**
     * Decide qué tipo de instrucción es una línea y la traduce.
     *
     * @param linea línea del código intermedio, sin espacios a los lados
     */
    private void traducirLinea(String linea) {
        if (linea.isEmpty()) {
            return;
        }
        if (linea.equals(FIN_PRINCIPAL)) {
            traducirFinPrograma();
        } else {
            texto.append(instruccion("# pendiente: " + linea));
        }
    }

    /**
     * Termina el programa con la llamada al sistema 10 (exit).
     */
    private void traducirFinPrograma() {
        texto.append(instruccion("li $v0, 10"));
        texto.append(instruccion("syscall"));
    }

    /**
     * Da formato a una instrucción MIPS: sangría y salto de línea.
     *
     * @param contenido instrucción
     * @return instrucción lista para agregar al programa
     */
    private static String instruccion(String contenido) {
        return "    " + contenido + "\n";
    }

    /**
     * Da formato a una etiqueta MIPS.
     *
     * @param nombre nombre de la etiqueta, ya válido en MIPS
     * @return etiqueta lista para agregar al programa
     */
    private static String etiqueta(String nombre) {
        return nombre + ":\n";
    }
}
