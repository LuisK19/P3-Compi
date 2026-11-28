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
    private static final String SECCION_GLOBALES = "main";
    private static final String SUFIJO_FIN_FUNCION = "#end";

    private static final int ESPACIO_ENLACE = 8;
    private static final int POSICION_RA = 4;
    private static final int POSICION_FP = 0;
    private static final int TAMANO_PALABRA = 4;
    private static final int PARAMETROS_EN_REGISTROS = 4;

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
        iniciarSeccion(SECCION_GLOBALES, lineas, 0);
        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i].trim();
            if (linea.isEmpty()) {
                continue;
            }
            if (esInicioDeFuncion(linea)) {
                String nombre = linea.substring(0, linea.length() - 1);
                iniciarSeccion(NombresMIPS.convertir(nombre), lineas, i + 1);
            } else {
                if (bloques.empiezaBloque(linea)) {
                    texto.append("# bloque basico " + bloques.getNumero() + "\n");
                }
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
     * espacio en la pila. Una función además guarda $ra y el $fp de quien la
     * llamó.
     *
     * @param nombre nombre de la sección en MIPS
     * @param lineas todas las líneas del código intermedio
     * @param desde posición de la primera línea de la sección
     */
    private void iniciarSeccion(String nombre, String[] lineas, int desde) {
        marco = crearMarco(lineas, desde);
        parametrosCopiados = 0;
        bloques.iniciarSeccion();
        texto.append(etiqueta(nombre));
        if (!nombre.equals(SECCION_GLOBALES)) {
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
     * Traduce "if t goto L": salta a L si el bool t no es cero.
     *
     * @param linea salto condicional del código intermedio
     */
    private void traducirSaltoCondicional(String linea) {
        String[] partes = linea.split(" ");
        String destino = NombresMIPS.convertir(partes[POSICION_DESTINO_SALTO]);
        cargar(partes[POSICION_CONDICION], "$t0");
        texto.append(instruccion("bne $t0, $zero, " + destino));
    }

    /**
     * Copia un parámetro a su lugar en el marco. Los primeros cuatro llegan en
     * $a0 a $a3; los demás, en la pila que dejó quien llamó, arriba del $ra y
     * el $fp guardados.
     *
     * @param linea declaración param# del código intermedio
     */
    private void traducirParametro(String linea) {
        String nombre = linea.split(" ")[1];
        if (parametrosCopiados < PARAMETROS_EN_REGISTROS) {
            guardar("$a" + parametrosCopiados, nombre);
        } else {
            int restantes = marco.getCantidadParametros() - 1 - parametrosCopiados;
            int desplazamiento = ESPACIO_ENLACE + TAMANO_PALABRA * restantes;
            texto.append(instruccion("lw $t0, " + desplazamiento + "($fp)"));
            guardar("$t0", nombre);
        }
        parametrosCopiados++;
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
     * Traduce una asignación: la copia de un valor, un texto literal o el
     * resultado de una operación entera. Los operandos se cargan en $t0 y $t1
     * y el resultado queda en $t0 antes de guardarse.
     *
     * @param linea asignación del código intermedio
     */
    private void traducirAsignacion(String linea) {
        int posicion = linea.indexOf(" = ");
        String destino = linea.substring(0, posicion);
        String valor = linea.substring(posicion + " = ".length());
        String[] partes = valor.split(" ");
        if (destino.contains("[")) {
            marcarPendiente(linea);
        } else if (valor.startsWith("\"")) {
            cargarTexto(valor, destino, "$t0");
            guardar("$t0", destino);
        } else if (esLiteralCaracter(valor)
                || (partes.length == PARTES_DE_UNA_COPIA && sePuedeCargar(valor))) {
            cargar(valor, "$t0");
            guardar("$t0", destino);
        } else if (partes.length == PARTES_DE_UNA_NEGACION && partes[0].equals(OPERADOR_NO)) {
            traducirNegacion(destino, partes[1]);
        } else if (partes.length == PARTES_DE_UNA_OPERACION) {
            traducirOperacion(linea, destino, partes);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Traduce una operación de la forma "izquierdo operador derecho" si es
     * entera o booleana; si no, la deja pendiente.
     *
     * @param linea asignación completa del código intermedio
     * @param destino nombre que recibe el resultado
     * @param partes operando izquierdo, operador y operando derecho
     */
    private void traducirOperacion(String linea, String destino, String[] partes) {
        String izquierdo = partes[0];
        String operador = partes[1];
        String derecho = partes[2];
        if (esOperacionEntera(destino, operador) || esOperacionBool(destino, operador, izquierdo)) {
            cargar(izquierdo, "$t0");
            cargar(derecho, "$t1");
            operar(operador);
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
     * Indica si una asignación es una operación que da un bool con
     * operandos que no son float. Los float se traducen en otra rama.
     *
     * @param destino nombre que recibe el resultado
     * @param operador operador de la expresión
     * @param izquierdo operando izquierdo
     * @return {@code true} si se traduce con instrucciones enteras
     */
    private boolean esOperacionBool(String destino, String operador, String izquierdo) {
        if (!destino.startsWith(PREFIJO_BOOL) || izquierdo.startsWith(PREFIJO_FLOTANTE)) {
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
     * Traduce la negación lógica: invierte el bit del bool.
     *
     * @param destino temporal bool que recibe el resultado
     * @param operando temporal bool negado
     */
    private void traducirNegacion(String destino, String operando) {
        cargar(operando, "$t0");
        texto.append(instruccion("xori $t0, $t0, " + VALOR_VERDADERO));
        guardar("$t0", destino);
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
     * @param literal true o false del código intermedio
     * @return 1 si es true, 0 si es false
     */
    private int valorDeBool(String literal) {
        if (literal.equals(LITERAL_VERDADERO)) {
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
     * @param registro registro destino
     */
    private void cargar(String operando, String registro) {
        if (esLiteralEntero(operando)) {
            texto.append(instruccion("li " + registro + ", " + operando));
        } else if (esLiteralBool(operando)) {
            texto.append(instruccion("li " + registro + ", " + valorDeBool(operando)));
        } else if (esLiteralCaracter(operando)) {
            int codigo = operando.charAt(POSICION_CARACTER);
            texto.append(instruccion("li " + registro + ", " + codigo));
        } else {
            texto.append(instruccion("lw " + registro + ", " + ubicacion(operando)));
        }
    }

    /**
     * Guarda un texto literal en la sección de datos y carga su dirección en
     * un registro. La etiqueta del texto lleva el número del temporal que lo
     * recibe en el código intermedio.
     *
     * @param literal texto del código intermedio, con sus comillas
     * @param temporal temporal string que recibe el texto
     * @param registro registro destino
     */
    private void cargarTexto(String literal, String temporal, String registro) {
        String numero = temporal.substring(temporal.lastIndexOf('#') + 1);
        String nombre = "string." + numero;
        datos.append(nombre + ": .asciiz " + literal + "\n");
        texto.append(instruccion("la " + registro + ", " + nombre));
    }

    /**
     * Traduce un write de int o de string con la llamada al sistema que
     * corresponde, seguida de un salto de línea.
     *
     * @param linea write del código intermedio
     */
    private void traducirWrite(String linea) {
        String[] partes = linea.split(" ");
        String tipo = partes[0].substring("write#".length());
        String nombre = partes[1];
        if (tipo.equals("int")) {
            escribirValor(nombre, LLAMADA_ESCRIBIR_ENTERO);
        } else if (tipo.equals("string")) {
            escribirValor(nombre, LLAMADA_ESCRIBIR_TEXTO);
        } else if (tipo.equals("bool")) {
            escribirBool(nombre);
        } else {
            marcarPendiente(linea);
        }
    }

    /**
     * Escribe en la consola el valor de una variable o un temporal y después
     * un salto de línea.
     *
     * @param nombre variable o temporal del código intermedio
     * @param codigoLlamada código de la llamada al sistema que escribe el valor
     */
    private void escribirValor(String nombre, int codigoLlamada) {
        cargar(nombre, "$a0");
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
     * @param nombre variable o temporal bool del código intermedio
     */
    private void escribirBool(String nombre) {
        guardarTextosBool();
        contadorEscriturasBool++;
        String falso = "write.bool.false." + contadorEscriturasBool;
        String fin = "write.bool.end." + contadorEscriturasBool;
        cargar(nombre, "$t0");
        texto.append(instruccion("beq $t0, $zero, " + falso));
        texto.append(instruccion("la $a0, " + ETIQUETA_BOOL_VERDADERO));
        texto.append(instruccion("j " + fin));
        texto.append(etiqueta(falso));
        texto.append(instruccion("la $a0, " + ETIQUETA_BOOL_FALSO));
        texto.append(etiqueta(fin));
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
        String[] partes = linea.split(" ");
        String tipo = partes[0].substring("read#".length());
        String nombre = partes[1];
        if (tipo.equals("int")) {
            llamarAlSistema(LLAMADA_LEER_ENTERO);
            guardar("$v0", nombre);
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
     * @param nombre nombre de la etiqueta, ya válido en MIPS
     * @return etiqueta lista para agregar al programa
     */
    private static String etiqueta(String nombre) {
        return nombre + ":\n";
    }
}
