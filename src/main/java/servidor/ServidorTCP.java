package servidor;

import java.io.*;
import java.net.*;
import java.sql.*;

public class ServidorTCP {
private static final int PUERTO = Integer.parseInt(System.getenv().getOrDefault("PORT", "12345"));
    private static Connection conexionBD;

    public static void main(String[] args) {
        try {
            // Conectar a la base de datos SQLite
            conectarBD();
            crearTablasSiNoExisten();

            // Iniciar servidor TCP
            ServerSocket serverSocket = new ServerSocket(PUERTO);
            System.out.println("Servidor iniciado en el puerto " + PUERTO);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new Thread(new ManejadorCliente(clientSocket)).start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void conectarBD() throws SQLException {
        conexionBD = DriverManager.getConnection("jdbc:sqlite:agricultores.db");
        System.out.println("Conectado a SQLite");
    }

    private static void crearTablasSiNoExisten() throws SQLException {
        Statement stmt = conexionBD.createStatement();

        // Tabla de usuarios
        stmt.execute("CREATE TABLE IF NOT EXISTS usuarios (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE NOT NULL, " +
                "password TEXT NOT NULL, " +
                "nombre TEXT NOT NULL)");

        // Tabla de productos con usuario_id
        stmt.execute("CREATE TABLE IF NOT EXISTS productos (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "usuario_id INTEGER NOT NULL, " +
                "nombre TEXT NOT NULL, " +
                "precio REAL NOT NULL, " +
                "stock REAL NOT NULL, " +
                "categoria TEXT NOT NULL, " +
                "unidad TEXT NOT NULL, " +
                "fecha_registro TEXT NOT NULL, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id))");

        // Tabla de ventas con usuario_id
        stmt.execute("CREATE TABLE IF NOT EXISTS ventas (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "usuario_id INTEGER NOT NULL, " +
                "fecha TEXT NOT NULL, " +
                "cliente TEXT NOT NULL, " +
                "total REAL NOT NULL, " +
                "detalles TEXT NOT NULL, " +
                "FOREIGN KEY(usuario_id) REFERENCES usuarios(id))");

        stmt.close();
    }

    private static class ManejadorCliente implements Runnable {
        private Socket socket;
        private BufferedReader entrada;
        private PrintWriter salida;

        public ManejadorCliente(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                salida = new PrintWriter(socket.getOutputStream(), true);

                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    System.out.println("Recibido: " + mensaje);
                    String respuesta = procesarMensaje(mensaje);
                    salida.println(respuesta);
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        private String procesarMensaje(String mensaje) {
            String[] partes = mensaje.split("\\|");
            String comando = partes[0];

            try {
                switch (comando) {
                    case "LOGIN":
                        return login(partes[1], partes[2]);
                    case "REGISTRO":
                        return registro(partes[1], partes[2], partes[3]);
                    case "AGREGAR_PRODUCTO":
                        if (partes.length < 8) {
                            return "ERROR|Faltan parámetros. Formato esperado: AGREGAR_PRODUCTO|usuario_id|nombre|precio|stock|categoria|unidad|fecha";
                        }
                        return agregarProducto(partes[1], partes[2], Double.parseDouble(partes[3]),
                                Double.parseDouble(partes[4]), partes[5], partes[6], partes[7]);
                    case "EDITAR_PRODUCTO":
                        return editarProducto(Integer.parseInt(partes[1]), partes[2],
                                Double.parseDouble(partes[3]), Double.parseDouble(partes[4]),
                                partes[5], partes[6], partes[7]);
                    case "ELIMINAR_PRODUCTO":
                        return eliminarProducto(Integer.parseInt(partes[1]));
                    case "OBTENER_PRODUCTOS":
                        // Formato esperado: OBTENER_PRODUCTOS|usuario_id
                        if (partes.length < 2) {
                            return "ERROR|Falta ID de usuario";
                        }
                        return obtenerProductos(partes[1]);
                    case "OBTENER_PRODUCTO":
                        return obtenerProductos(partes[1]);
                    case "REALIZAR_VENTA":
                        if (partes.length < 6) {
                            return "ERROR|Faltan parámetros. Formato esperado: REALIZAR_VENTA|usuario_id|cliente|fecha|total|detalles";
                        }
                        return realizarVenta(partes[1], partes[2], partes[3],
                                Double.parseDouble(partes[4]), partes[5]);
                    case "OBTENER_VENTAS":
                        if (partes.length < 2) {
                            return "ERROR|Falta ID de usuario";
                        }
                        return obtenerVentas(partes[1]); // Pasar el usuarioId
                    case "ELIMINAR_VENTA":
                        return eliminarVenta(Integer.parseInt(partes[1]));
                    case "OBTENER_ESTADISTICAS":
                        if (partes.length < 2)
                            return "ERROR|Falta ID de usuario";
                        return obtenerEstadisticas(partes[1]);
                    case "OBTENER_ALERTAS":
                        if (partes.length < 2)
                            return "ERROR|Falta ID de usuario";
                        return obtenerAlertas(partes[1]);
                    default:
                        return "ERROR|Comando no reconocido";
                }
            } catch (Exception e) {
                e.printStackTrace();
                return "ERROR|" + e.getMessage();
            }
        }

        private String login(String username, String password) throws SQLException {
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT id, nombre FROM usuarios WHERE username = ? AND password = ?");
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return "OK|" + rs.getInt("id") + "|" + rs.getString("nombre");
            } else {
                return "ERROR|Usuario o contraseña incorrectos";
            }
        }

        private String registro(String username, String password, String nombre) throws SQLException {
            try {
                // Verificar si el usuario ya existe
                PreparedStatement checkStmt = conexionBD.prepareStatement(
                        "SELECT username FROM usuarios WHERE username = ?");
                checkStmt.setString(1, username);
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    return "ERROR|El nombre de usuario ya existe";
                }

                // Insertar nuevo usuario
                PreparedStatement stmt = conexionBD.prepareStatement(
                        "INSERT INTO usuarios (username, password, nombre) VALUES (?, ?, ?)");
                stmt.setString(1, username);
                stmt.setString(2, password);
                stmt.setString(3, nombre);
                stmt.executeUpdate();

                return "OK|Registro exitoso";
            } catch (SQLException e) {
                if (e.getMessage().contains("UNIQUE constraint failed")) {
                    return "ERROR|El nombre de usuario ya existe";
                }
                return "ERROR|Error en el registro: " + e.getMessage();
            }
        }

        private String agregarProducto(String usuarioId, String nombre, double precio, double stock,
                String categoria, String unidad, String fecha) throws SQLException {
            try {
                PreparedStatement stmt = conexionBD.prepareStatement(
                        "INSERT INTO productos (usuario_id, nombre, precio, stock, categoria, unidad, fecha_registro) "
                                +
                                "VALUES (?, ?, ?, ?, ?, ?, ?)");

                stmt.setInt(1, Integer.parseInt(usuarioId));
                stmt.setString(2, nombre);
                stmt.setDouble(3, precio);
                stmt.setDouble(4, stock);
                stmt.setString(5, categoria);
                stmt.setString(6, unidad);
                stmt.setString(7, fecha);

                stmt.executeUpdate();
                return "OK|Producto agregado correctamente";
            } catch (SQLException e) {
                return "ERROR|Error al agregar producto: " + e.getMessage();
            }
        }

        private String editarProducto(int id, String nombre, double precio, double stock,
                String categoria, String unidad, String fecha) throws SQLException {
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "UPDATE productos SET nombre = ?, precio = ?, stock = ?, categoria = ?, " +
                            "unidad = ?, fecha_registro = ? WHERE id = ?");
            stmt.setString(1, nombre);
            stmt.setDouble(2, precio);
            stmt.setDouble(3, stock);
            stmt.setString(4, categoria);
            stmt.setString(5, unidad);
            stmt.setString(6, fecha);
            stmt.setInt(7, id);
            int filas = stmt.executeUpdate();

            if (filas > 0) {
                return "OK|Producto actualizado";
            } else {
                return "ERROR|Producto no encontrado";
            }
        }

        private String eliminarProducto(int id) throws SQLException {
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "DELETE FROM productos WHERE id = ?");
            stmt.setInt(1, id);
            int filas = stmt.executeUpdate();

            if (filas > 0) {
                return "OK|Producto eliminado";
            } else {
                return "ERROR|Producto no encontrado";
            }
        }

        private String obtenerProductos(String usuarioId) throws SQLException {
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT id, nombre, precio, stock, categoria, unidad, fecha_registro " +
                            "FROM productos WHERE usuario_id = ? ORDER BY nombre");
            stmt.setInt(1, Integer.parseInt(usuarioId));
            ResultSet rs = stmt.executeQuery();

            StringBuilder sb = new StringBuilder();
            boolean hayProductos = false;

            while (rs.next()) {
                hayProductos = true;
                sb.append(String.format("%d|%s|%.2f|%.2f|%s|%s|%s;",
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getDouble("precio"),
                        rs.getDouble("stock"),
                        rs.getString("categoria"),
                        rs.getString("unidad"),
                        rs.getString("fecha_registro")));
            }

            stmt.close();
            return hayProductos ? "OK|" + sb.toString() : "OK|NO_HAY";
        }

        private String obtenerProducto(int id) throws SQLException {
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT * FROM productos WHERE id = ?");
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return String.format("OK|%d|%s|%.2f|%.2f|%s|%s|%s",
                        rs.getInt("id"),
                        rs.getString("nombre"),
                        rs.getDouble("precio"),
                        rs.getDouble("stock"),
                        rs.getString("categoria"),
                        rs.getString("unidad"),
                        rs.getString("fecha_registro"));
            } else {
                return "ERROR|Producto no encontrado";
            }
        }

        private String realizarVenta(String usuarioId, String cliente, String fecha, double total, String detalles)
                throws SQLException {
            try {
                // Iniciar transacción
                conexionBD.setAutoCommit(false);

                // 1. Insertar la venta
                PreparedStatement stmt = conexionBD.prepareStatement(
                        "INSERT INTO ventas (usuario_id, cliente, fecha, total, detalles) VALUES (?, ?, ?, ?, ?)");
                stmt.setInt(1, Integer.parseInt(usuarioId));
                stmt.setString(2, cliente);
                stmt.setString(3, fecha);
                stmt.setDouble(4, total);
                stmt.setString(5, detalles);
                stmt.executeUpdate();

                // 2. Actualizar los stocks de los productos
                actualizarStocks(detalles);

                // Confirmar transacción
                conexionBD.commit();
                return "OK|Venta registrada y stocks actualizados";
            } catch (SQLException e) {
                // Revertir en caso de error
                conexionBD.rollback();
                return "ERROR|Error al registrar venta: " + e.getMessage();
            } finally {
                conexionBD.setAutoCommit(true);
            }
        }

        private void actualizarStocks(String detalles) throws SQLException {
            String[] items = detalles.split(";");
            for (String item : items) {
                String[] partes = item.split(",");
                int idProducto = Integer.parseInt(partes[0]);
                double cantidad = Double.parseDouble(partes[1]);

                PreparedStatement stmt = conexionBD.prepareStatement(
                        "UPDATE productos SET stock = stock - ? WHERE id = ?");
                stmt.setDouble(1, cantidad);
                stmt.setInt(2, idProducto);
                stmt.executeUpdate();
            }
        }

        private String obtenerVentas(String usuarioId) throws SQLException {
            // Verificar que el usuarioId no sea null
            if (usuarioId == null || usuarioId.equalsIgnoreCase("null")) {
                return "ERROR|ID de usuario inválido";
            }

            try {
                int idUsuario = Integer.parseInt(usuarioId);
                PreparedStatement stmt = conexionBD.prepareStatement(
                        "SELECT id, fecha, cliente, total, detalles FROM ventas WHERE usuario_id = ? ORDER BY fecha DESC");
                stmt.setInt(1, idUsuario);
                ResultSet rs = stmt.executeQuery();

                StringBuilder sb = new StringBuilder();
                boolean hayVentas = false;

                while (rs.next()) {
                    hayVentas = true;
                    sb.append(String.format("%d|%s|%s|%.2f|%s;",
                            rs.getInt("id"),
                            rs.getString("fecha"),
                            rs.getString("cliente"),
                            rs.getDouble("total"),
                            rs.getString("detalles")));
                }

                stmt.close();
                return hayVentas ? "OK|" + sb.toString() : "OK|NO_HAY";
            } catch (NumberFormatException e) {
                return "ERROR|ID de usuario debe ser numérico";
            } catch (SQLException e) {
                return "ERROR|Error en la base de datos: " + e.getMessage();
            }
        }

        private String eliminarVenta(int id) throws SQLException {
            // Primero obtener los detalles para reponer stock
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT detalles FROM ventas WHERE id = ?");
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String detalles = rs.getString("detalles");
                reponerStocks(detalles);

                // Luego eliminar la venta
                stmt = conexionBD.prepareStatement("DELETE FROM ventas WHERE id = ?");
                stmt.setInt(1, id);
                int filas = stmt.executeUpdate();

                if (filas > 0) {
                    return "OK|Venta eliminada y stock repuesto";
                }
            }

            return "ERROR|Venta no encontrada";
        }

        private void reponerStocks(String detalles) throws SQLException {
            String[] items = detalles.split(";");
            for (String item : items) {
                String[] partes = item.split(",");
                int idProducto = Integer.parseInt(partes[0]);
                double cantidad = Double.parseDouble(partes[1]);

                PreparedStatement stmt = conexionBD.prepareStatement(
                        "UPDATE productos SET stock = stock + ? WHERE id = ?");
                stmt.setDouble(1, cantidad);
                stmt.setInt(2, idProducto);
                stmt.executeUpdate();
            }
        }

        private String obtenerEstadisticas(String usuarioId) throws SQLException {
            StringBuilder sb = new StringBuilder();
            int idUsuario = Integer.parseInt(usuarioId);

            // 1. Total productos del usuario
            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT COUNT(*) as total FROM productos WHERE usuario_id = ?");
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();
            rs.next();
            sb.append("TOTAL_PRODUCTOS=").append(rs.getInt("total")).append(";");

            // 2. Productos por categoría del usuario
            stmt = conexionBD.prepareStatement(
                    "SELECT categoria, COUNT(*) as cantidad FROM productos WHERE usuario_id = ? GROUP BY categoria");
            stmt.setInt(1, idUsuario);
            rs = stmt.executeQuery();
            while (rs.next()) {
                sb.append("CAT_").append(rs.getString("categoria")).append("=")
                        .append(rs.getInt("cantidad")).append(";");
            }

            // 3. Valor total del inventario del usuario
            stmt = conexionBD.prepareStatement(
                    "SELECT SUM(precio * stock) as total FROM productos WHERE usuario_id = ?");
            stmt.setInt(1, idUsuario);
            rs = stmt.executeQuery();
            rs.next();
            sb.append("VALOR_INVENTARIO=").append(rs.getDouble("total")).append(";");

            // 4. Valor por categoría del usuario
            stmt = conexionBD.prepareStatement(
                    "SELECT categoria, SUM(precio * stock) as total FROM productos WHERE usuario_id = ? GROUP BY categoria");
            stmt.setInt(1, idUsuario);
            rs = stmt.executeQuery();
            while (rs.next()) {
                sb.append("VALOR_CAT_").append(rs.getString("categoria")).append("=")
                        .append(rs.getDouble("total")).append(";");
            }

            // 5. Total ventas del usuario
            stmt = conexionBD.prepareStatement(
                    "SELECT SUM(total) as total FROM ventas WHERE usuario_id = ?");
            stmt.setInt(1, idUsuario);
            rs = stmt.executeQuery();
            rs.next();
            sb.append("TOTAL_VENTAS=").append(rs.getDouble("total")).append(";");

            stmt.close();
            return "OK|" + sb.toString();
        }

        private String obtenerAlertas(String usuarioId) throws SQLException {
            StringBuilder sb = new StringBuilder();
            int idUsuario = Integer.parseInt(usuarioId);

            PreparedStatement stmt = conexionBD.prepareStatement(
                    "SELECT nombre, stock FROM productos WHERE usuario_id = ? AND stock < 10 ORDER BY stock ASC");
            stmt.setInt(1, idUsuario);
            ResultSet rs = stmt.executeQuery();

            boolean hayAlertas = false;

            while (rs.next()) {
                hayAlertas = true;
                sb.append(String.format("BAJO_STOCK|%s|%.2f;",
                        rs.getString("nombre"),
                        rs.getDouble("stock")));
            }

            stmt.close();
            return hayAlertas ? "OK|" + sb.toString() : "OK|NO_HAY";
        }

    }
}