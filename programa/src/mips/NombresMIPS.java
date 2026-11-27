package src.mips;

/**
 * Convierte los nombres del código intermedio en nombres válidos en MIPS.
 *
 * <p>En MIPS el carácter # inicia un comentario, así que no puede formar parte
 * de un nombre.
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
