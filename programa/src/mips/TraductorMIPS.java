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
    private int contadorPotencias = 0;

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
        } else if (linea.contains(" = ")) {
            traducirAsignacion(linea);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Deja como comentario una línea que todavía no se traduce.
     *
     * @param linea línea del código intermedio
     */
    private void marcarPendiente(String linea) {
        texto.append(instruccion("# pendiente: " + linea));
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
     * Traduce una asignación: la copia de un valor o el resultado de una
     * operación entera. Los operandos se cargan en $t0 y $t1 y el resultado
     * queda en $t0 antes de guardarse.
     *
     * @param linea asignación del código intermedio
     */
    private void traducirAsignacion(String linea) {
        int posicion = linea.indexOf(" = ");
        String destino = linea.substring(0, posicion);
        String[] partes = linea.substring(posicion + " = ".length()).split(" ");
        if (destino.contains("[")) {
            marcarPendiente(linea);
        } else if (partes.length == 1 && sePuedeCargar(partes[0])) {
            cargar(partes[0], "$t0");
            guardar("$t0", destino);
        } else if (partes.length == 3 && esOperacionEntera(destino, partes[1])) {
            cargar(partes[0], "$t0");
            cargar(partes[2], "$t1");
            operar(partes[1]);
            guardar("$t0", destino);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Indica si una asignación es una operación entera: el destino es un
     * temporal int y el operador es aritmético.
     *
     * @param destino nombre que recibe el resultado
     * @param operador operador de la expresión
     * @return {@code true} si se traduce con instrucciones enteras
     */
    private boolean esOperacionEntera(String destino, String operador) {
        if (!destino.startsWith("temp#int#")) {
            return false;
        }
        return operador.equals("+") || operador.equals("-") || operador.equals("*")
                || operador.equals("//") || operador.equals("mod") || operador.equals("pot");
    }

    /**
     * Escribe la operación entera entre $t0 y $t1, con el resultado en $t0.
     *
     * @param operador operador aritmético del código intermedio
     */
    private void operar(String operador) {
        switch (operador) {
            case "+":
                texto.append(instruccion("add $t0, $t0, $t1"));
                break;
            case "-":
                texto.append(instruccion("sub $t0, $t0, $t1"));
                break;
            case "*":
                texto.append(instruccion("mul $t0, $t0, $t1"));
                break;
            case "//":
                texto.append(instruccion("div $t0, $t1"));
                texto.append(instruccion("mflo $t0"));
                break;
            case "mod":
                texto.append(instruccion("div $t0, $t1"));
                texto.append(instruccion("mfhi $t0"));
                break;
            case "pot":
                traducirPotencia();
                break;
            default:
                break;
        }
    }

    /**
     * Calcula $t0 elevado a $t1 con un ciclo de multiplicaciones. El resultado
     * se acumula en $t2 y al final se pasa a $t0.
     */
    private void traducirPotencia() {
        contadorPotencias++;
        String inicio = "pot." + contadorPotencias;
        String fin = "pot.end." + contadorPotencias;
        texto.append(instruccion("li $t2, 1"));
        texto.append(etiqueta(inicio));
        texto.append(instruccion("blez $t1, " + fin));
        texto.append(instruccion("mul $t2, $t2, $t0"));
        texto.append(instruccion("addi $t1, $t1, -1"));
        texto.append(instruccion("j " + inicio));
        texto.append(etiqueta(fin));
        texto.append(instruccion("move $t0, $t2"));
    }

    /**
     * Indica si un operando se puede cargar en un registro con lo que ya
     * traduce el programa: un literal entero, una variable o un temporal.
     *
     * @param operando operando del código intermedio
     * @return {@code true} si se puede cargar
     */
    private boolean sePuedeCargar(String operando) {
        return esLiteralEntero(operando) || marco.contiene(operando)
                || operando.endsWith("#global");
    }

    /**
     * Indica si un operando es un literal entero, positivo o negativo.
     *
     * @param operando operando del código intermedio
     * @return {@code true} si es un literal entero
     */
    private boolean esLiteralEntero(String operando) {
        return operando.matches("-?[0-9]+");
    }

    /**
     * Carga un operando en un registro: un literal con li y una variable o un
     * temporal con lw.
     *
     * @param operando literal, variable o temporal del código intermedio
     * @param registro registro destino
     */
    private void cargar(String operando, String registro) {
        if (esLiteralEntero(operando)) {
            texto.append(instruccion("li " + registro + ", " + operando));
        } else {
            texto.append(instruccion("lw " + registro + ", " + ubicacion(operando)));
        }
    }

    /**
     * Guarda el valor de un registro en una variable o un temporal.
     *
     * @param registro registro con el valor
     * @param nombre variable o temporal del código intermedio
     */
    private void guardar(String registro, String nombre) {
        texto.append(instruccion("sw " + registro + ", " + ubicacion(nombre)));
    }

    /**
     * Devuelve dónde está guardado un nombre: su lugar en el marco si es una
     * local o un temporal, o su etiqueta en la sección de datos si es global.
     *
     * @param nombre variable o temporal del código intermedio
     * @return dirección lista para usar en lw o sw
     */
    private String ubicacion(String nombre) {
        if (marco.contiene(nombre)) {
            return marco.ubicacion(nombre);
        }
        return NombresMIPS.convertir(nombre);
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
