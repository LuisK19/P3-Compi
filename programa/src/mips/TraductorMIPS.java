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
    private MarcoFuncion marco;

    /**
     * Traduce un programa completo por secciones: main, donde se inicializan
     * las globales, y cada función, cada una con su propio marco.
     *
     * @param codigoIntermedio código intermedio generado por el parser, sin errores
     * @return código MIPS del programa
     */
    public String traducir(String codigoIntermedio) {
        String[] lineas = codigoIntermedio.split("\n");
        iniciarSeccion("main", lineas, 0);
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (esInicioDeFuncion(linea)) {
                String nombre = linea.substring(0, linea.length() - 1);
                iniciarSeccion(NombresMIPS.convertir(nombre), lineas, i + 1);
            } else {
                traducirLinea(linea);
            }
        }
        return ".data\n" + datos + "\n.text\n.globl main\n" + texto;
    }

    /**
     * Indica si una línea es la etiqueta con la que empieza una función.
     *
     * @param linea línea del código intermedio
     * @return {@code true} si empieza una función
     */
    private boolean esInicioDeFuncion(String linea) {
        return linea.startsWith("funcion#") && linea.endsWith(":");
    }

    /**
     * Empieza una sección: arma su marco, escribe su etiqueta y reserva el
     * espacio en la pila.
     *
     * @param nombre nombre de la sección en MIPS
     * @param lineas todas las líneas del código intermedio
     * @param desde posición de la primera línea de la sección
     */
    private void iniciarSeccion(String nombre, String[] lineas, int desde) {
        marco = crearMarco(lineas, desde);
        texto.append(etiqueta(nombre));
        texto.append(instruccion("move $fp, $sp"));
        if (marco.getTamano() > 0) {
            texto.append(instruccion("addi $sp, $sp, -" + marco.getTamano()));
        }
    }

    /**
     * Arma el marco de una sección recorriendo sus líneas hasta la siguiente
     * función o el final del código.
     *
     * @param lineas todas las líneas del código intermedio
     * @param desde posición de la primera línea de la sección
     * @return marco con las locales y los temporales de la sección
     */
    private MarcoFuncion crearMarco(String[] lineas, int desde) {
        MarcoFuncion nuevo = new MarcoFuncion();
        int actual = desde;
        while (actual < lineas.length && !esInicioDeFuncion(lineas[actual].trim())) {
            nuevo.registrar(lineas[actual].trim());
            actual++;
        }
        return nuevo;
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
        } else if (linea.endsWith(":")) {
            traducirEtiqueta(linea);
        } else if (linea.startsWith("goto ")) {
            traducirSalto(linea);
        } else if (linea.startsWith("var#")) {
            traducirDeclaracion(linea);
        } else {
            texto.append(instruccion("# pendiente: " + linea));
        }
    }

    /**
     * Traduce una etiqueta.
     *
     * @param linea etiqueta del código intermedio, con los dos puntos al final
     */
    private void traducirEtiqueta(String linea) {
        String nombre = linea.substring(0, linea.length() - 1);
        texto.append(etiqueta(NombresMIPS.convertir(nombre)));
    }

    /**
     * Traduce un salto incondicional a la instrucción j.
     *
     * @param linea salto del código intermedio
     */
    private void traducirSalto(String linea) {
        String destino = linea.substring("goto ".length());
        texto.append(instruccion("j " + NombresMIPS.convertir(destino)));
    }

    /**
     * Traduce la declaración de una variable. Una global se reserva en la
     * sección de datos con valor inicial cero; una local no genera código.
     *
     * @param linea declaración var# del código intermedio
     */
    private void traducirDeclaracion(String linea) {
        String nombre = linea.split(" ")[1];
        if (nombre.endsWith("#global")) {
            datos.append(NombresMIPS.convertir(nombre) + ": .word 0\n");
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
