package src;

import generated.Lexer;
import generated.Parser;
import generated.sym;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java_cup.runtime.Symbol;
import src.mips.TraductorMIPS;
import src.semantico.RecolectorFunciones;

/**
 * Ejecuta las fases del compilador sobre un archivo fuente.
 *
 * <p>Lee los tokens, ejecuta el parser y genera los reportes de tokens, de la
 * tabla de símbolos y del código intermedio, y el código MIPS.
 */
public class Analizador {
    private static final int ARGUMENTO_ENTRADA = 0;
    private static final int ARGUMENTO_TOKENS = 1;
    private static final int ARGUMENTO_TABLA = 2;
    private static final int ARGUMENTO_CODIGO = 3;

    /**
     * Inicia el análisis usando las rutas indicadas en los argumentos.
     *
     * <p>El primer argumento es el archivo de entrada, el segundo la ruta del
     * reporte CSV de tokens, el tercero la ruta del reporte de la tabla de
     * símbolos y el cuarto la ruta del código intermedio. Si no se
     * proporcionan, los reportes se guardan en la carpeta salida. El código
     * MIPS se escribe junto al código intermedio, con el nombre del archivo de
     * entrada y la extensión .asm.
     *
     * @param args rutas opcionales de entrada y de los reportes
     * @throws IOException si no se pueden crear las carpetas de los reportes
     */
    public static void main(String[] args) throws IOException {
        usarSalidaUtf8();

        String archivoEntrada = argumento(args, ARGUMENTO_ENTRADA, "tests/validos/programa_completo.txt");
        String archivoSalidaTokens = argumento(args, ARGUMENTO_TOKENS, "salida/tokens_encontrados.csv");
        String archivoSalidaTabla = argumento(args, ARGUMENTO_TABLA, "salida/tabla_simbolos.txt");
        String archivoSalidaCodigo = argumento(args, ARGUMENTO_CODIGO, "salida/codigo_intermedio.txt");

        if (!new File(archivoEntrada).exists()) {
            System.err.println("No se encontro el archivo de entrada: " + archivoEntrada);
            return;
        }

        crearCarpetaDe(archivoSalidaTokens);
        crearCarpetaDe(archivoSalidaTabla);
        crearCarpetaDe(archivoSalidaCodigo);

        System.out.println("--- INICIANDO COMPILADOR ---");
        System.out.println("Archivo de entrada: " + archivoEntrada);

        ArrayList<Symbol> tokens = leerTokens(archivoEntrada);
        if (tokens == null) {
            System.out.println("\nRESULTADO: No se pudo leer el archivo fuente.");
            return;
        }

        ejecutarAnalisisSintactico(archivoEntrada, tokens, archivoSalidaTokens, archivoSalidaTabla, archivoSalidaCodigo);
    }

    /**
     * Devuelve un argumento de la línea de comandos, o un valor por defecto si
     * no se proporcionó.
     *
     * @param args argumentos recibidos
     * @param posicion posición del argumento
     * @param valorPorDefecto valor que se usa si el argumento no existe
     * @return argumento o valor por defecto
     */
    private static String argumento(String[] args, int posicion, String valorPorDefecto) {
        if (posicion < args.length) {
            return args[posicion];
        }
        return valorPorDefecto;
    }

    /**
     * Crea la carpeta donde va un archivo de salida, si todavía no existe.
     *
     * @param rutaArchivo ruta del archivo que se va a escribir
     * @throws IOException si no se puede crear la carpeta
     */
    private static void crearCarpetaDe(String rutaArchivo) throws IOException {
        Path carpeta = Path.of(rutaArchivo).getParent();
        if (carpeta != null) {
            Files.createDirectories(carpeta);
        }
    }

    /**
     * Configura la salida de consola en UTF-8.
     */
    private static void usarSalidaUtf8() {
        System.setOut(new PrintStream(System.out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(System.err, true, StandardCharsets.UTF_8));
    }

    /**
     * Lee todos los tokens léxicos del archivo de entrada.
     *
     * <p>Los errores léxicos se registran en la salida de errores y no impiden
     * conservar los demás tokens reconocidos.
     *
     * @param rutaEntrada ruta del archivo fuente que se analizará
     * @return tokens reconocidos, o {@code null} si no se pudo leer el archivo
     */
    public static ArrayList<Symbol> leerTokens(String rutaEntrada) {
        try (BufferedReader lector = Files.newBufferedReader(Path.of(rutaEntrada), StandardCharsets.UTF_8)) {
            Lexer analizadorLexico = new Lexer(lector);
            ArrayList<Symbol> tokens = new ArrayList<>();
            Symbol tokenActual;

            while ((tokenActual = analizadorLexico.next_token()).sym != sym.EOF) {
                tokens.add(tokenActual);
            }

            if (analizadorLexico.huboErroresLexicos()) {
                System.err.print(analizadorLexico.obtenerErroresLexicos());
                System.out.println("ERROR LEXICO: Se encontraron errores lexicos.");
            }
            return tokens;
        } catch (Exception e) {
            System.out.println("ERROR LEXICO/ARCHIVO: No se pudieron leer los tokens.");
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Ejecuta el parser, escribe los reportes, comunica si la entrada cumple la
     * gramática y, si no hubo errores, escribe el código intermedio y el código
     * MIPS.
     *
     * @param rutaEntrada ruta del archivo fuente que se analizará
     * @param tokens tokens leídos del archivo de entrada
     * @param rutaTokens ruta del archivo CSV de tokens
     * @param rutaTabla ruta del archivo donde se escribirá la tabla de símbolos
     * @param rutaCodigo ruta del archivo donde se escribirá el código intermedio
     */
    public static void ejecutarAnalisisSintactico(String rutaEntrada, ArrayList<Symbol> tokens, String rutaTokens,
            String rutaTabla, String rutaCodigo) {
        try (BufferedReader lector = Files.newBufferedReader(Path.of(rutaEntrada), StandardCharsets.UTF_8)) {
            Lexer analizadorLexico = new Lexer(lector);
            Parser analizadorSintactico = new Parser(analizadorLexico);
            analizadorSintactico.setFuncionesDeclaradas(RecolectorFunciones.recolectar(rutaEntrada));

            System.out.println("\nIniciando analisis sintactico...");
            try {
                analizadorSintactico.parse();
            } finally {
                analizadorSintactico.getReporteTokens().escribir(tokens, rutaTokens);
                System.out.println("Reporte de tokens generado en -> " + rutaTokens);
            }

            analizadorSintactico.getTablaSimbolos().escribirReporte(rutaTabla);
            System.out.println("Tabla de simbolos generada en -> " + rutaTabla);

            if (analizadorLexico.huboErroresLexicos()
                    || analizadorSintactico.huboErroresSintacticos()
                    || analizadorSintactico.huboErroresSemanticos()) {
                System.out.println("No se genero el codigo intermedio ni el codigo MIPS porque el archivo fuente"
                        + " tiene errores.");
                System.out.println(
                        "\nRESULTADO: El archivo fuente NO puede ser generado por la gramatica (Errores encontrados).");
                return;
            }

            String codigoIntermedio = analizadorSintactico.getCodigoIntermedio();
            Files.writeString(Path.of(rutaCodigo), codigoIntermedio, StandardCharsets.UTF_8);
            System.out.println("Codigo intermedio generado en -> " + rutaCodigo);
            escribirCodigoMips(codigoIntermedio, rutaEntrada, rutaCodigo);
            System.out.println("\nRESULTADO: El archivo fuente SI puede ser generado por la gramatica.");
        } catch (Exception e) {
            System.err.println("Error durante el analisis: " + e.getMessage());
            System.out.println(
                    "\nRESULTADO: El archivo fuente NO puede ser generado por la gramatica (Errores encontrados).");
        }
    }

    /**
     * Traduce el código intermedio a MIPS y lo escribe en su archivo.
     *
     * @param codigoIntermedio código intermedio generado por el parser
     * @param rutaEntrada ruta del archivo fuente
     * @param rutaCodigo ruta del archivo de código intermedio
     * @throws IOException si no se puede escribir el archivo
     */
    private static void escribirCodigoMips(String codigoIntermedio, String rutaEntrada, String rutaCodigo)
            throws IOException {
        String rutaMips = rutaCodigoMips(rutaEntrada, rutaCodigo);
        String codigoMips = new TraductorMIPS().traducir(codigoIntermedio);
        Files.writeString(Path.of(rutaMips), codigoMips, StandardCharsets.UTF_8);
        System.out.println("Codigo MIPS generado en -> " + rutaMips);
    }

    /**
     * Arma la ruta del archivo MIPS: el nombre del archivo de entrada con la
     * extensión .asm, en la misma carpeta que el código intermedio.
     *
     * @param rutaEntrada ruta del archivo fuente
     * @param rutaCodigo ruta del archivo de código intermedio
     * @return ruta del archivo MIPS
     */
    private static String rutaCodigoMips(String rutaEntrada, String rutaCodigo) {
        String nombreEntrada = Path.of(rutaEntrada).getFileName().toString();
        int posicionPunto = nombreEntrada.lastIndexOf('.');
        String nombreSinExtension = nombreEntrada;
        if (posicionPunto > 0) {
            nombreSinExtension = nombreEntrada.substring(0, posicionPunto);
        }

        String nombreArchivoCodigo = Path.of(rutaCodigo).getFileName().toString();
        int largoCarpeta = rutaCodigo.length() - nombreArchivoCodigo.length();
        String carpetaCodigo = rutaCodigo.substring(0, largoCarpeta);
        return carpetaCodigo + nombreSinExtension + ".asm";
    }
}
