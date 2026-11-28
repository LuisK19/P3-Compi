package src.mips;

import java.util.HashMap;

/**
 * Espacio que ocupa en la pila una sección del código intermedio: la
 * inicialización de globales o una función.
 *
 * <p>Cada variable local y cada temporal recibe un lugar fijo de una palabra
 * debajo de $fp, que se usa durante toda la sección.
 */
public class MarcoFuncion {
    private static final int TAMANO_PALABRA = 4;

    private HashMap<String, Integer> desplazamientos = new HashMap<>();
    private int tamano = 0;
    private int cantidadParametros = 0;

    /**
     * Anota los nombres que crea una línea de la sección: un parámetro, la
     * variable de un var# local o el temporal que recibe un valor. Las demás
     * líneas no crean nombres.
     *
     * @param linea línea del código intermedio, sin espacios a los lados
     */
    public void registrar(String linea) {
        if (linea.startsWith("param#")) {
            cantidadParametros++;
            agregar(linea.split(" ")[1]);
        } else if (linea.startsWith("var#")) {
            agregar(linea.split(" ")[1]);
        } else if (linea.startsWith("temp#") && linea.contains(" = ")) {
            agregar(linea.substring(0, linea.indexOf(" = ")));
        }
    }

    /**
     * Le asigna un lugar en el marco a un nombre, si es local y todavía no lo
     * tiene.
     *
     * @param nombre variable local o temporal
     */
    private void agregar(String nombre) {
        if (nombre.endsWith("#global") || desplazamientos.containsKey(nombre)) {
            return;
        }
        tamano += TAMANO_PALABRA;
        desplazamientos.put(nombre, -tamano);
    }

    /**
     * Indica si un nombre tiene un lugar en este marco.
     *
     * @param nombre nombre del código intermedio
     * @return {@code true} si es una local o un temporal de la sección
     */
    public boolean contiene(String nombre) {
        return desplazamientos.containsKey(nombre);
    }

    /**
     * Devuelve la dirección de un nombre del marco, relativa a $fp.
     *
     * @param nombre variable local o temporal registrado en el marco
     * @return dirección lista para usar en lw o sw
     */
    public String ubicacion(String nombre) {
        return desplazamientos.get(nombre) + "($fp)";
    }

    /** Devuelve cuántos bytes ocupa el marco. */
    public int getTamano() {
        return tamano;
    }

    /** Devuelve cuántos parámetros recibe la función. */
    public int getCantidadParametros() {
        return cantidadParametros;
    }
}
