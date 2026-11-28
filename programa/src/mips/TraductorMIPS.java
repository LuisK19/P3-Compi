package src.mips;

import java.util.HashMap;

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
    private static final String SECCION_GLOBALES = "main";
    private static final String SUFIJO_FIN_FUNCION = "#end";

    private static final int ESPACIO_ENLACE = 8;
    private static final int POSICION_RA = 4;
    private static final int POSICION_FP = 0;
    private static final int TAMANO_PALABRA = 4;
    private static final int PARAMETROS_EN_REGISTROS = 4;
    private static final String TOPE_PILA = "0($sp)";

    private static final int LLAMADA_ESCRIBIR_ENTERO = 1;
    private static final int LLAMADA_ESCRIBIR_TEXTO = 4;
    private static final int LLAMADA_LEER_ENTERO = 5;
    private static final int LLAMADA_TERMINAR = 10;
    private static final int LLAMADA_ESCRIBIR_CARACTER = 11;
    private static final int CARACTER_SALTO_DE_LINEA = 10;

    private static final int PARTES_DE_UNA_COPIA = 1;
    private static final int PARTES_DE_UNA_OPERACION = 3;

    private static final String LITERAL_VERDADERO = "true";
    private static final String LITERAL_FALSO = "false";
    private static final int VALOR_VERDADERO = 1;
    private static final int VALOR_FALSO = 0;
    private static final String COMILLA_CARACTER = "'";
    private static final int LARGO_LITERAL_CARACTER = 3;
    private static final int POSICION_CARACTER = 1;

    private static final String PREFIJO_BOOL = "temp#bool#";
    private static final String PREFIJO_FLOTANTE = "temp#float#";

    private static final int POSICION_CONDICION = 1;
    private static final int POSICION_DESTINO_SALTO = 3;

    private static final String ETIQUETA_BOOL_VERDADERO = "bool.true";
    private static final String ETIQUETA_BOOL_FALSO = "bool.false";
    private static final String TEXTO_VERDADERO = "\"true\"";
    private static final String TEXTO_FALSO = "\"false\"";

    private static final String OPERADOR_Y = "\u03bb";
    private static final String OPERADOR_O = "\u03b8";
    private static final String OPERADOR_NO = "\u03a3";
    private static final int PARTES_DE_UNA_NEGACION = 2;

    private StringBuilder datos = new StringBuilder();
    private StringBuilder texto = new StringBuilder();
    private MarcoFuncion marco;
    private BloquesBasicos bloques;
    private HashMap<String, String> etiquetasFunciones = new HashMap<>();
    private int parametrosCopiados = 0;
    private int contadorPotencias = 0;
    private int contadorEscriturasBool = 0;
    private boolean textosBoolGuardados = false;

    /**
     * Traduce un programa completo por secciones: main, donde se inicializan
     * las globales, y cada función, cada una con su propio marco. Al inicio de
     * cada bloque básico se escribe un comentario con su número.
     *
     * @param codigoIntermedio código intermedio generado por el parser, sin errores
     * @return código MIPS del programa
     */
    public String traducir(String codigoIntermedio) {
        String[] lineas = codigoIntermedio.split("\n");
        bloques = new BloquesBasicos(lineas);
        anotarFunciones(lineas);
        iniciarSeccion(SECCION_GLOBALES, lineas, 0);
        for (int numeroLinea = 0; numeroLinea < lineas.length; numeroLinea++) {
            String linea = lineas[numeroLinea].trim();
            if (linea.isEmpty()) {
                continue;
            }
            if (esInicioDeFuncion(linea)) {
                String etiquetaIntermedia = linea.substring(0, linea.length() - 1);
                iniciarSeccion(NombresMIPS.convertir(etiquetaIntermedia), lineas, numeroLinea + 1);
            } else {
                if (bloques.empiezaBloque(linea)) {
                    texto.append("# bloque basico " + bloques.getNumeroBloque() + "\n");
                }
                traducirLinea(linea);
            }
        }
        return ".data\n" + datos + "\n.text\n.globl main\n" + texto;
    }

    /**
     * Anota la etiqueta MIPS de cada función según su nombre, porque una
     * llamada del código intermedio solo trae el nombre.
     *
     * @param lineas todas las líneas del código intermedio
     */
    private void anotarFunciones(String[] lineas) {
        for (String lineaOriginal : lineas) {
            String linea = lineaOriginal.trim();
            if (esInicioDeFuncion(linea)) {
                String etiquetaIntermedia = linea.substring(0, linea.length() - 1);
                int posicionUltimoNumeral = etiquetaIntermedia.lastIndexOf('#');
                String nombreFuncion = etiquetaIntermedia.substring(posicionUltimoNumeral + 1);
                etiquetasFunciones.put(nombreFuncion, NombresMIPS.convertir(etiquetaIntermedia));
            }
        }
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
     * espacio en la pila. Una función además guarda $ra y el $fp de quien la
     * llamó.
     *
     * @param nombreSeccion nombre de la sección en MIPS
     * @param lineas todas las líneas del código intermedio
     * @param posicionInicio posición de la primera línea de la sección
     */
    private void iniciarSeccion(String nombreSeccion, String[] lineas, int posicionInicio) {
        marco = crearMarco(lineas, posicionInicio);
        parametrosCopiados = 0;
        bloques.iniciarSeccion();
        texto.append(etiqueta(nombreSeccion));
        if (!nombreSeccion.equals(SECCION_GLOBALES)) {
            guardarEnlace();
        }
        texto.append(instruccion("move $fp, $sp"));
        if (marco.getTamano() > 0) {
            texto.append(instruccion("addi $sp, $sp, -" + marco.getTamano()));
        }
    }

    /**
     * Guarda en la pila la dirección de regreso y el $fp de quien llamó a la
     * función.
     */
    private void guardarEnlace() {
        texto.append(instruccion("addi $sp, $sp, -" + ESPACIO_ENLACE));
        texto.append(instruccion("sw $ra, " + POSICION_RA + "($sp)"));
        texto.append(instruccion("sw $fp, " + POSICION_FP + "($sp)"));
    }

    /**
     * Arma el marco de una sección recorriendo sus líneas hasta la siguiente
     * función o el final del código.
     *
     * @param lineas todas las líneas del código intermedio
     * @param posicionInicio posición de la primera línea de la sección
     * @return marco con las locales y los temporales de la sección
     */
    private MarcoFuncion crearMarco(String[] lineas, int posicionInicio) {
        MarcoFuncion marcoSeccion = new MarcoFuncion();
        int posicionActual = posicionInicio;
        while (posicionActual < lineas.length
                && !esInicioDeFuncion(lineas[posicionActual].trim())) {
            marcoSeccion.registrar(lineas[posicionActual].trim());
            posicionActual++;
        }
        return marcoSeccion;
    }

    /**
     * Decide qué tipo de instrucción es una línea y la traduce.
     *
     * @param linea línea del código intermedio, sin espacios a los lados
     */
    private void traducirLinea(String linea) {
        if (linea.equals(FIN_PRINCIPAL)) {
            traducirFinPrograma();
        } else if (linea.startsWith("funcion#") && linea.endsWith(SUFIJO_FIN_FUNCION)) {
            traducirFinFuncion(linea);
        } else if (linea.endsWith(":")) {
            traducirEtiqueta(linea);
        } else if (linea.startsWith("goto ")) {
            traducirSalto(linea);
        } else if (linea.startsWith("if ")) {
            traducirSaltoCondicional(linea);
        } else if (linea.startsWith("param#")) {
            traducirParametro(linea);
        } else if (linea.startsWith("var#")) {
            traducirDeclaracion(linea);
        } else if (linea.startsWith("write#")) {
            traducirWrite(linea);
        } else if (linea.startsWith("read#")) {
            traducirRead(linea);
        } else if (linea.startsWith("param ")) {
            traducirArgumento(linea);
        } else if (linea.startsWith("call ")) {
            traducirLlamada(linea);
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
        String etiquetaIntermedia = linea.substring(0, linea.length() - 1);
        texto.append(etiqueta(NombresMIPS.convertir(etiquetaIntermedia)));
    }

    /**
     * Traduce un salto incondicional a la instrucción j.
     *
     * @param linea salto del código intermedio
     */
    private void traducirSalto(String linea) {
        String etiquetaDestino = linea.substring("goto ".length());
        texto.append(instruccion("j " + NombresMIPS.convertir(etiquetaDestino)));
    }

    /**
     * Traduce "if t goto L": salta a L si el bool t no es cero.
     *
     * @param linea salto condicional del código intermedio
     */
    private void traducirSaltoCondicional(String linea) {
        String[] partesSalto = linea.split(" ");
        String etiquetaDestino = NombresMIPS.convertir(partesSalto[POSICION_DESTINO_SALTO]);
        cargar(partesSalto[POSICION_CONDICION], "$t0");
        texto.append(instruccion("bne $t0, $zero, " + etiquetaDestino));
    }

    /**
     * Copia un parámetro a su lugar en el marco. Los primeros cuatro llegan en
     * $a0 a $a3; los demás, en la pila que dejó quien llamó, arriba del $ra y
     * el $fp guardados.
     *
     * @param linea declaración param# del código intermedio
     */
    private void traducirParametro(String linea) {
        String nombreParametro = linea.split(" ")[1];
        if (parametrosCopiados < PARAMETROS_EN_REGISTROS) {
            guardar("$a" + parametrosCopiados, nombreParametro);
        } else {
            int parametrosDespues = marco.getCantidadParametros() - 1 - parametrosCopiados;
            int desplazamientoEnPila = ESPACIO_ENLACE + TAMANO_PALABRA * parametrosDespues;
            texto.append(instruccion("lw $t0, " + desplazamientoEnPila + "($fp)"));
            guardar("$t0", nombreParametro);
        }
        parametrosCopiados++;
    }

    /**
     * Traduce "param t": apila el valor de un argumento antes de la llamada.
     *
     * @param linea argumento del código intermedio
     */
    private void traducirArgumento(String linea) {
        String valorArgumento = linea.substring("param ".length());
        cargar(valorArgumento, "$t0");
        texto.append(instruccion("addi $sp, $sp, -" + TAMANO_PALABRA));
        texto.append(instruccion("sw $t0, " + TOPE_PILA));
    }

    /**
     * Traduce "call f, n": copia los primeros cuatro argumentos apilados en
     * $a0 a $a3, salta a la función y, al volver, saca los n argumentos de la
     * pila. El resultado de la función queda en $v0.
     *
     * @param llamada llamada del código intermedio, sin el destino
     */
    private void traducirLlamada(String llamada) {
        int posicionComa = llamada.indexOf(", ");
        String nombreFuncion = llamada.substring("call ".length(), posicionComa);
        int cantidadArgumentos = Integer.parseInt(llamada.substring(posicionComa + ", ".length()));
        int argumentosEnRegistros = Math.min(cantidadArgumentos, PARAMETROS_EN_REGISTROS);
        for (int numeroArgumento = 0; numeroArgumento < argumentosEnRegistros; numeroArgumento++) {
            int argumentosDespues = cantidadArgumentos - 1 - numeroArgumento;
            int desplazamientoEnPila = TAMANO_PALABRA * argumentosDespues;
            String registroArgumento = "$a" + numeroArgumento;
            String direccionArgumento = desplazamientoEnPila + "($sp)";
            texto.append(instruccion("lw " + registroArgumento + ", " + direccionArgumento));
        }
        texto.append(instruccion("jal " + etiquetasFunciones.get(nombreFuncion)));
        if (cantidadArgumentos > 0) {
            texto.append(instruccion("addi $sp, $sp, " + TAMANO_PALABRA * cantidadArgumentos));
        }
    }

    /**
     * Traduce la declaración de una variable. Una global se reserva en la
     * sección de datos con valor inicial cero; una local no genera código.
     *
     * @param linea declaración var# del código intermedio
     */
    private void traducirDeclaracion(String linea) {
        String nombreVariable = linea.split(" ")[1];
        if (nombreVariable.endsWith("#global")) {
            datos.append(NombresMIPS.convertir(nombreVariable) + ": .word 0\n");
        }
    }

    /**
     * Traduce una asignación: la copia de un valor, un texto literal o el
     * resultado de una operación entera. Los operandos se cargan en $t0 y $t1
     * y el resultado queda en $t0 antes de guardarse.
     *
     * @param linea asignación del código intermedio
     */
    private void traducirAsignacion(String linea) {
        int posicionIgual = linea.indexOf(" = ");
        String nombreDestino = linea.substring(0, posicionIgual);
        String valorAsignado = linea.substring(posicionIgual + " = ".length());
        String[] partesValor = valorAsignado.split(" ");
        if (nombreDestino.contains("[")) {
            marcarPendiente(linea);
        } else if (valorAsignado.startsWith("call ")) {
            traducirLlamada(valorAsignado);
            guardar("$v0", nombreDestino);
        } else if (valorAsignado.startsWith("\"")) {
            cargarTexto(valorAsignado, nombreDestino, "$t0");
            guardar("$t0", nombreDestino);
        } else if (esLiteralCaracter(valorAsignado)
                || (partesValor.length == PARTES_DE_UNA_COPIA && sePuedeCargar(valorAsignado))) {
            cargar(valorAsignado, "$t0");
            guardar("$t0", nombreDestino);
        } else if (partesValor.length == PARTES_DE_UNA_NEGACION
                && partesValor[0].equals(OPERADOR_NO)) {
            traducirNegacion(nombreDestino, partesValor[1]);
        } else if (partesValor.length == PARTES_DE_UNA_OPERACION) {
            traducirOperacion(linea, nombreDestino, partesValor);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Traduce una operación de la forma "izquierdo operador derecho" si es
     * entera o booleana; si no, la deja pendiente.
     *
     * @param linea asignación completa del código intermedio
     * @param nombreDestino nombre que recibe el resultado
     * @param partesOperacion operando izquierdo, operador y operando derecho
     */
    private void traducirOperacion(String linea, String nombreDestino, String[] partesOperacion) {
        String operandoIzquierdo = partesOperacion[0];
        String operador = partesOperacion[1];
        String operandoDerecho = partesOperacion[2];
        if (esOperacionEntera(nombreDestino, operador)
                || esOperacionBool(nombreDestino, operador, operandoIzquierdo)) {
            cargar(operandoIzquierdo, "$t0");
            cargar(operandoDerecho, "$t1");
            operar(operador);
            guardar("$t0", nombreDestino);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Indica si una asignación es una operación entera: el destino es un
     * temporal int y el operador es aritmético.
     *
     * @param nombreDestino nombre que recibe el resultado
     * @param operador operador de la expresión
     * @return {@code true} si se traduce con instrucciones enteras
     */
    private boolean esOperacionEntera(String nombreDestino, String operador) {
        if (!nombreDestino.startsWith("temp#int#")) {
            return false;
        }
        return operador.equals("+") || operador.equals("-") || operador.equals("*")
                || operador.equals("//") || operador.equals("mod") || operador.equals("pot");
    }

    /**
     * Indica si una asignación es una operación que da un bool con
     * operandos que no son float. Los float se traducen en otra rama.
     *
     * @param nombreDestino nombre que recibe el resultado
     * @param operador operador de la expresión
     * @param operandoIzquierdo operando izquierdo
     * @return {@code true} si se traduce con instrucciones enteras
     */
    private boolean esOperacionBool(String nombreDestino, String operador,
            String operandoIzquierdo) {
        if (!nombreDestino.startsWith(PREFIJO_BOOL)
                || operandoIzquierdo.startsWith(PREFIJO_FLOTANTE)) {
            return false;
        }
        return esRelacional(operador) || operador.equals(OPERADOR_Y) || operador.equals(OPERADOR_O);
    }

    /**
     * Indica si un operador es relacional.
     *
     * @param operador operador de la expresión
     * @return {@code true} si compara dos valores
     */
    private boolean esRelacional(String operador) {
        return operador.equals("<") || operador.equals("<=") || operador.equals(">")
                || operador.equals(">=") || operador.equals("==") || operador.equals("!=");
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
            case "<":
                texto.append(instruccion("slt $t0, $t0, $t1"));
                break;
            case "<=":
                texto.append(instruccion("sle $t0, $t0, $t1"));
                break;
            case ">":
                texto.append(instruccion("sgt $t0, $t0, $t1"));
                break;
            case ">=":
                texto.append(instruccion("sge $t0, $t0, $t1"));
                break;
            case "==":
                texto.append(instruccion("seq $t0, $t0, $t1"));
                break;
            case "!=":
                texto.append(instruccion("sne $t0, $t0, $t1"));
                break;
            case OPERADOR_Y:
                texto.append(instruccion("and $t0, $t0, $t1"));
                break;
            case OPERADOR_O:
                texto.append(instruccion("or $t0, $t0, $t1"));
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
        String etiquetaInicio = "pot." + contadorPotencias;
        String etiquetaFin = "pot.end." + contadorPotencias;
        texto.append(instruccion("li $t2, 1"));
        texto.append(etiqueta(etiquetaInicio));
        texto.append(instruccion("blez $t1, " + etiquetaFin));
        texto.append(instruccion("mul $t2, $t2, $t0"));
        texto.append(instruccion("addi $t1, $t1, -1"));
        texto.append(instruccion("j " + etiquetaInicio));
        texto.append(etiqueta(etiquetaFin));
        texto.append(instruccion("move $t0, $t2"));
    }

    /**
     * Traduce la negación lógica: invierte el bit del bool.
     *
     * @param nombreDestino temporal bool que recibe el resultado
     * @param operandoNegado temporal bool negado
     */
    private void traducirNegacion(String nombreDestino, String operandoNegado) {
        cargar(operandoNegado, "$t0");
        texto.append(instruccion("xori $t0, $t0, " + VALOR_VERDADERO));
        guardar("$t0", nombreDestino);
    }

    /**
     * Indica si un operando se puede cargar en un registro con lo que ya
     * traduce el programa: un literal entero, bool o char, una variable o un
     * temporal.
     *
     * @param operando operando del código intermedio
     * @return {@code true} si se puede cargar
     */
    private boolean sePuedeCargar(String operando) {
        return esLiteralEntero(operando) || esLiteralBool(operando)
                || esLiteralCaracter(operando) || marco.contiene(operando)
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
     * Indica si un operando es el literal true o false.
     *
     * @param operando operando del código intermedio
     * @return {@code true} si es un literal bool
     */
    private boolean esLiteralBool(String operando) {
        return operando.equals(LITERAL_VERDADERO) || operando.equals(LITERAL_FALSO);
    }

    /**
     * Indica si un operando es un literal char: un carácter entre comillas
     * simples, incluido el espacio.
     *
     * @param operando operando del código intermedio
     * @return {@code true} si es un literal char
     */
    private boolean esLiteralCaracter(String operando) {
        return operando.length() == LARGO_LITERAL_CARACTER
                && operando.startsWith(COMILLA_CARACTER)
                && operando.endsWith(COMILLA_CARACTER);
    }

    /**
     * Devuelve el valor numérico de un literal bool.
     *
     * @param literalBool true o false del código intermedio
     * @return 1 si es true, 0 si es false
     */
    private int valorDeBool(String literalBool) {
        if (literalBool.equals(LITERAL_VERDADERO)) {
            return VALOR_VERDADERO;
        }
        return VALOR_FALSO;
    }

    /**
     * Carga un operando en un registro: un literal con li y una variable o un
     * temporal con lw. Un bool se carga como 0 o 1 y un char como su código
     * ASCII.
     *
     * @param operando literal, variable o temporal del código intermedio
     * @param registroDestino registro que recibe el valor
     */
    private void cargar(String operando, String registroDestino) {
        if (esLiteralEntero(operando)) {
            texto.append(instruccion("li " + registroDestino + ", " + operando));
        } else if (esLiteralBool(operando)) {
            texto.append(instruccion("li " + registroDestino + ", " + valorDeBool(operando)));
        } else if (esLiteralCaracter(operando)) {
            int codigoAscii = operando.charAt(POSICION_CARACTER);
            texto.append(instruccion("li " + registroDestino + ", " + codigoAscii));
        } else {
            texto.append(instruccion("lw " + registroDestino + ", " + ubicacion(operando)));
        }
    }

    /**
     * Guarda un texto literal en la sección de datos y carga su dirección en
     * un registro. La etiqueta del texto lleva el número del temporal que lo
     * recibe en el código intermedio.
     *
     * @param textoLiteral texto del código intermedio, con sus comillas
     * @param temporalDestino temporal string que recibe el texto
     * @param registroDestino registro que recibe la dirección
     */
    private void cargarTexto(String textoLiteral, String temporalDestino, String registroDestino) {
        int posicionUltimoNumeral = temporalDestino.lastIndexOf('#');
        String numeroTemporal = temporalDestino.substring(posicionUltimoNumeral + 1);
        String etiquetaTexto = "string." + numeroTemporal;
        datos.append(etiquetaTexto + ": .asciiz " + textoLiteral + "\n");
        texto.append(instruccion("la " + registroDestino + ", " + etiquetaTexto));
    }

    /**
     * Traduce un write de int o de string con la llamada al sistema que
     * corresponde, seguida de un salto de línea.
     *
     * @param linea write del código intermedio
     */
    private void traducirWrite(String linea) {
        String[] partesWrite = linea.split(" ");
        String tipoValor = partesWrite[0].substring("write#".length());
        String nombreValor = partesWrite[1];
        if (tipoValor.equals("int")) {
            escribirValor(nombreValor, LLAMADA_ESCRIBIR_ENTERO);
        } else if (tipoValor.equals("string")) {
            escribirValor(nombreValor, LLAMADA_ESCRIBIR_TEXTO);
        } else if (tipoValor.equals("bool")) {
            escribirBool(nombreValor);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Escribe en la consola el valor de una variable o un temporal y después
     * un salto de línea.
     *
     * @param nombreValor variable o temporal del código intermedio
     * @param codigoLlamada código de la llamada al sistema que escribe el valor
     */
    private void escribirValor(String nombreValor, int codigoLlamada) {
        cargar(nombreValor, "$a0");
        llamarAlSistema(codigoLlamada);
        escribirSaltoDeLinea();
    }

    /**
     * Escribe un salto de línea en la consola.
     */
    private void escribirSaltoDeLinea() {
        texto.append(instruccion("li $a0, " + CARACTER_SALTO_DE_LINEA));
        llamarAlSistema(LLAMADA_ESCRIBIR_CARACTER);
    }

    /**
     * Escribe true o false según el valor de un bool, seguido de un salto de
     * línea.
     *
     * @param nombreBool variable o temporal bool del código intermedio
     */
    private void escribirBool(String nombreBool) {
        guardarTextosBool();
        contadorEscriturasBool++;
        String etiquetaFalso = "write.bool.false." + contadorEscriturasBool;
        String etiquetaFin = "write.bool.end." + contadorEscriturasBool;
        cargar(nombreBool, "$t0");
        texto.append(instruccion("beq $t0, $zero, " + etiquetaFalso));
        texto.append(instruccion("la $a0, " + ETIQUETA_BOOL_VERDADERO));
        texto.append(instruccion("j " + etiquetaFin));
        texto.append(etiqueta(etiquetaFalso));
        texto.append(instruccion("la $a0, " + ETIQUETA_BOOL_FALSO));
        texto.append(etiqueta(etiquetaFin));
        llamarAlSistema(LLAMADA_ESCRIBIR_TEXTO);
        escribirSaltoDeLinea();
    }

    /**
     * Guarda en la sección de datos los textos true y false, una sola vez.
     */
    private void guardarTextosBool() {
        if (textosBoolGuardados) {
            return;
        }
        datos.append(ETIQUETA_BOOL_VERDADERO + ": .asciiz " + TEXTO_VERDADERO + "\n");
        datos.append(ETIQUETA_BOOL_FALSO + ": .asciiz " + TEXTO_FALSO + "\n");
        textosBoolGuardados = true;
    }

    /**
     * Traduce un read de int: lee el número con una llamada al sistema y lo
     * guarda en la variable.
     *
     * @param linea read del código intermedio
     */
    private void traducirRead(String linea) {
        String[] partesRead = linea.split(" ");
        String tipoVariable = partesRead[0].substring("read#".length());
        String nombreVariable = partesRead[1];
        if (tipoVariable.equals("int")) {
            llamarAlSistema(LLAMADA_LEER_ENTERO);
            guardar("$v0", nombreVariable);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Hace una llamada al sistema: pone su código en $v0 y ejecuta syscall.
     *
     * @param codigoLlamada código de la llamada al sistema
     */
    private void llamarAlSistema(int codigoLlamada) {
        texto.append(instruccion("li $v0, " + codigoLlamada));
        texto.append(instruccion("syscall"));
    }

    /**
     * Guarda el valor de un registro en una variable o un temporal.
     *
     * @param registroOrigen registro con el valor
     * @param nombreDestino variable o temporal del código intermedio
     */
    private void guardar(String registroOrigen, String nombreDestino) {
        texto.append(instruccion("sw " + registroOrigen + ", " + ubicacion(nombreDestino)));
    }

    /**
     * Devuelve dónde está guardado un nombre: su lugar en el marco si es una
     * local o un temporal, o su etiqueta en la sección de datos si es global.
     *
     * @param nombreBuscado variable o temporal del código intermedio
     * @return dirección lista para usar en lw o sw
     */
    private String ubicacion(String nombreBuscado) {
        if (marco.contiene(nombreBuscado)) {
            return marco.ubicacion(nombreBuscado);
        }
        return NombresMIPS.convertir(nombreBuscado);
    }

    /**
     * Termina el programa con la llamada al sistema exit.
     */
    private void traducirFinPrograma() {
        llamarAlSistema(LLAMADA_TERMINAR);
    }

    /**
     * Traduce el final de una función: escribe su etiqueta de salida, saca el
     * marco de la pila, recupera $fp y $ra y regresa a quien la llamó.
     *
     * @param linea final de la función en el código intermedio
     */
    private void traducirFinFuncion(String linea) {
        texto.append(etiqueta(NombresMIPS.convertir(linea)));
        texto.append(instruccion("move $sp, $fp"));
        texto.append(instruccion("lw $fp, " + POSICION_FP + "($sp)"));
        texto.append(instruccion("lw $ra, " + POSICION_RA + "($sp)"));
        texto.append(instruccion("addi $sp, $sp, " + ESPACIO_ENLACE));
        texto.append(instruccion("jr $ra"));
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
     * @param nombreEtiqueta nombre de la etiqueta, ya válido en MIPS
     * @return etiqueta lista para agregar al programa
     */
    private static String etiqueta(String nombreEtiqueta) {
        return nombreEtiqueta + ":\n";
    }
}
