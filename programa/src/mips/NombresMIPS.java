package src.mips;

/**
 * Convierte los nombres del código intermedio en nombres válidos en MIPS.
 */
public class NombresMIPS {

    /**
     * Convierte un nombre del código intermedio cambiando cada # por un punto.
     *
     * @param nombre variable global, etiqueta o función del código intermedio
     * @return nombre válido en MIPS
     */
    public static String convertir(String nombre) {
        return nombre.replace('#', '.');
    }
}
