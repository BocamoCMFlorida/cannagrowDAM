package com.example.cannagrow;

import com.example.model.Session;
import com.example.model.UsuarioModel;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.IOException;
import java.sql.*;
import java.util.Optional;
import java.util.function.Predicate;

import com.example.model.DBUtil;

/**
 * Controlador para la vista de administración de usuarios.
 * Permite a un gerente gestionar empleados y clientes mediante filtros, edición, eliminación y estadísticas.
 *
 * Carga datos desde la base de datos y presenta una interfaz interactiva con filtros y botones de acción.
 *
 * Acceso limitado a usuarios con rol "Gerente".
 *
 * FXML asociado: /com/example/cannagrow/admin-usuarios.fxml
 */
public class AdminUsuariosController {

    @FXML
    private TableView<UsuarioTablaModel> tablaUsuarios;

    @FXML
    private TableColumn<UsuarioTablaModel, Integer> colId;

    @FXML
    private TableColumn<UsuarioTablaModel, String> colNombre;

    @FXML
    private TableColumn<UsuarioTablaModel, String> colEmail;

    @FXML
    private TableColumn<UsuarioTablaModel, String> colRol;

    @FXML
    private TableColumn<UsuarioTablaModel, Double> colSalario;

    @FXML
    private TableColumn<UsuarioTablaModel, ImageView> colFoto;

    @FXML
    private TableColumn<UsuarioTablaModel, Void> colAcciones;

    @FXML
    private ComboBox<String> comboFiltroRol;

    @FXML
    private TextField txtBuscar;

    @FXML
    private Button btnBuscar;

    @FXML
    private Button btnLimpiarFiltros;

    @FXML
    private Button btnAgregarUsuario;

    @FXML
    private Label lblTotalUsuarios;

    @FXML
    private Label lblTotalEmpleados;

    @FXML
    private Label lblTotalClientes;

    private ObservableList<UsuarioTablaModel> listaUsuarios = FXCollections.observableArrayList();
    private FilteredList<UsuarioTablaModel> listaFiltrada;

    /**
     * Inicializa la interfaz de administración de usuarios.
     * Verifica permisos, configura la tabla, filtros, eventos y carga los datos iniciales.
     */
    @FXML
    public void initialize() {
        // Verificar permisos
        UsuarioModel usuarioActual = Session.getUsuarioActual();
        if (usuarioActual == null || !usuarioActual.getRol().equalsIgnoreCase("Gerente")) {
            mostrarMensajeError("Acceso denegado", "No tienes permisos para acceder a esta sección.");
            return;
        }

        // Inicializar la tabla
        configurarColumnas();

        // Inicializar el combo de filtros
        comboFiltroRol.getItems().addAll("Todos", "Gerente", "Vendedor", "Almacenista", "Cliente");
        comboFiltroRol.setValue("Todos");

        // Configurar los eventos
        configurarEventos();

        // Cargar datos
        cargarUsuarios();

        // Configurar filtros
        configurarFiltros();

        // Actualizar estadísticas
        actualizarEstadisticas();
    }

    /**
     * Configura las columnas de la tabla de usuarios, incluyendo nombre, email, rol, salario, foto y botones de acción.
     */
    private void configurarColumnas() {
        colId.setCellValueFactory(cellData -> new SimpleIntegerProperty(cellData.getValue().getId()).asObject());
        colNombre.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getNombre()));
        colEmail.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getEmail()));
        colRol.setCellValueFactory(cellData -> new SimpleStringProperty(cellData.getValue().getRol()));
        colSalario.setCellValueFactory(cellData -> {
            if (cellData.getValue().getRol().equalsIgnoreCase("Cliente")) {
                return new SimpleDoubleProperty(0.0).asObject();
            } else {
                return new SimpleDoubleProperty(cellData.getValue().getSalario()).asObject();
            }
        });

        // Configurar la columna de fotos
        colFoto.setCellValueFactory(cellData -> {
            String fotoUrl = cellData.getValue().getFotoPerfilUrl();
            if (fotoUrl != null && !fotoUrl.isEmpty()) {
                try {
                    Image imagen = new Image(getClass().getResourceAsStream(fotoUrl), 40, 40, true, true);
                    ImageView imageView = new ImageView(imagen);
                    imageView.setFitHeight(40);
                    imageView.setFitWidth(40);
                    return new SimpleObjectProperty<>(imageView);
                } catch (Exception e) {
                    // Si hay error al cargar la imagen, usar una predeterminada
                    Image imagenPredeterminada = new Image(getClass().getResourceAsStream("/com/example/cannagrow/img/perfil_cliente.png"), 40, 40, true, true);
                    ImageView imageView = new ImageView(imagenPredeterminada);
                    imageView.setFitHeight(40);
                    imageView.setFitWidth(40);
                    return new SimpleObjectProperty<>(imageView);
                }
            } else {
                return null;
            }
        });

        // Configurar la columna de acciones con botones
        colAcciones.setCellFactory(param -> new TableCell<>() {
            private final Button btnEditar = new Button("Editar");
            private final Button btnEliminar = new Button("Eliminar");
            private final HBox pane = new HBox(5, btnEditar, btnEliminar);

            {
                btnEditar.setStyle("-fx-background-color: #1976d2; -fx-text-fill: white; -fx-font-size: 10px;");
                btnEliminar.setStyle("-fx-background-color: #d32f2f; -fx-text-fill: white; -fx-font-size: 10px;");
                pane.setAlignment(Pos.CENTER);

                btnEditar.setOnAction(event -> {
                    UsuarioTablaModel usuario = getTableView().getItems().get(getIndex());
                    abrirDialogoEdicion(usuario);
                });

                btnEliminar.setOnAction(event -> {
                    UsuarioTablaModel usuario = getTableView().getItems().get(getIndex());
                    confirmarEliminacion(usuario);
                });
            }
            /**
             * Actualizar item
             */
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : pane);
            }
        });
    }

    /**
     * Asocia eventos a botones como agregar, buscar y limpiar filtros, así como a cambios en el combo de rol.
     */
    private void configurarEventos() {
        btnAgregarUsuario.setOnAction(event -> abrirDialogoAgregar());

        btnBuscar.setOnAction(event -> aplicarFiltros());

        btnLimpiarFiltros.setOnAction(event -> {
            txtBuscar.clear();
            comboFiltroRol.setValue("Todos");
            listaFiltrada.setPredicate(usuario -> true);
            actualizarEstadisticas();
        });

        comboFiltroRol.setOnAction(event -> aplicarFiltros());
    }

    /**
     * Aplica filtros a la lista de usuarios según el texto ingresado y el rol seleccionado.
     */
    private void configurarFiltros() {
        listaFiltrada = new FilteredList<>(listaUsuarios, p -> true);
        tablaUsuarios.setItems(listaFiltrada);
    }

    /**
     * Configura la lista filtrada para aplicar filtros dinámicos sobre la tabla de usuarios.
     */
    private void aplicarFiltros() {
        String textoBusqueda = txtBuscar.getText().toLowerCase().trim();
        String rolSeleccionado = comboFiltroRol.getValue();

        Predicate<UsuarioTablaModel> predicado = usuario -> {
            boolean coincideTexto = textoBusqueda.isEmpty() ||
                    usuario.getNombre().toLowerCase().contains(textoBusqueda) ||
                    usuario.getEmail().toLowerCase().contains(textoBusqueda);

            boolean coincideRol = "Todos".equals(rolSeleccionado) ||
                    usuario.getRol().equalsIgnoreCase(rolSeleccionado);

            return coincideTexto && coincideRol;
        };

        listaFiltrada.setPredicate(predicado);
        actualizarEstadisticas();
    }

    /**
     * Carga todos los usuarios desde la base de datos, incluyendo empleados y clientes.
     */
    private void cargarUsuarios() {
        listaUsuarios.clear();

        // Cargar empleados
        cargarDesdeTabla("Empleado");

        // Cargar clientes
        cargarDesdeTabla("Cliente");
    }

    /**
     * Carga usuarios desde una tabla específica (Empleado o Cliente) de la base de datos.
     *
     * @param tabla Nombre de la tabla ("Empleado" o "Cliente").
     */
    private void cargarDesdeTabla(String tabla) {
        String query = "SELECT id, nombre, email, fotoPerfilUrl, discordid" +
                (tabla.equals("Empleado") ? ", rol, salario" : "") +
                " FROM " + tabla;

        try (Connection conn = DBUtil.getConexion();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                UsuarioTablaModel usuario = new UsuarioTablaModel();
                usuario.setId(rs.getInt("id"));
                usuario.setNombre(rs.getString("nombre"));
                usuario.setEmail(rs.getString("email"));
                usuario.setFotoPerfilUrl(rs.getString("fotoPerfilUrl"));
                usuario.setDiscordId(rs.getString("discordid"));

                if (tabla.equals("Empleado")) {
                    usuario.setRol(rs.getString("rol"));
                    usuario.setSalario(rs.getDouble("salario"));
                    usuario.setEsEmpleado(true);
                } else {
                    usuario.setRol("Cliente");
                    usuario.setSalario(0);
                    usuario.setEsEmpleado(false);
                }

                listaUsuarios.add(usuario);
            }

        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensajeError("Error de base de datos", "Error al cargar los usuarios: " + e.getMessage());
        }
    }

    /**
     * Abre el diálogo de formulario para agregar un nuevo usuario.
     * Se invoca el guardado según el tipo (empleado o cliente).
     */
    private void actualizarEstadisticas() {
        int totalUsuarios = listaFiltrada.size();
        int totalEmpleados = 0;
        int totalClientes = 0;

        for (UsuarioTablaModel usuario : listaFiltrada) {
            if (usuario.getRol().equalsIgnoreCase("Cliente")) {
                totalClientes++;
            } else {
                totalEmpleados++;
            }
        }

        lblTotalUsuarios.setText("Total Usuarios: " + totalUsuarios);
        lblTotalEmpleados.setText("Empleados: " + totalEmpleados);
        lblTotalClientes.setText("Clientes: " + totalClientes);
    }

    /**
     * Abre el diálogo de edición de usuario con los datos prellenados.
     *
     *
     */
    private void abrirDialogoAgregar() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/cannagrow/dialogo-usuario.fxml"));
            Scene scene = new Scene(loader.load());

            DialogoUsuarioController controller = loader.getController();
            controller.setModoEdicion(false);

            // Configurar evento de guardado
            controller.setOnGuardarListener((usuario, esEmpleado) -> {
                if (esEmpleado) {
                    boolean resultado = new UsuarioModel().registrarUsuario(
                            usuario.getNombre(),
                            usuario.getEmail(),
                            controller.getContrasena(),
                            usuario.getRol(),
                            usuario.getSalario(),
                            usuario.getDiscordId()
                    );
                    if (resultado) {
                        cargarUsuarios();
                        aplicarFiltros();
                        return true;
                    }
                } else {
                    boolean resultado = new UsuarioModel().registrarCliente(
                            usuario.getNombre(),
                            usuario.getEmail(),
                            controller.getContrasena(),
                            controller.getFechaNacimiento(),
                            usuario.getDiscordId()
                    );
                    if (resultado) {
                        cargarUsuarios();
                        aplicarFiltros();
                        return true;
                    }
                }
                return false;
            });

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Agregar Usuario");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            mostrarMensajeError("Error", "No se pudo abrir el diálogo: " + e.getMessage());
        }
    }

    /**
     * Actualiza los datos de un usuario existente en la base de datos.
     *
     * @param usuario Usuario con los nuevos datos.
     * @return true si se actualizó correctamente, false en caso contrario.
     */
    private void abrirDialogoEdicion(UsuarioTablaModel usuario) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/example/cannagrow/dialogo-usuario.fxml"));
            Scene scene = new Scene(loader.load());

            DialogoUsuarioController controller = loader.getController();
            controller.setModoEdicion(true);
            controller.setUsuario(usuario);

            // Configurar evento de guardado para edición
            controller.setOnGuardarListener((usuarioEditado, esEmpleado) -> {
                boolean resultado = actualizarUsuario(usuarioEditado, esEmpleado);
                if (resultado) {
                    cargarUsuarios();
                    aplicarFiltros();
                    return true;
                }
                return false;
            });

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Editar Usuario");
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.setScene(scene);
            dialogStage.showAndWait();

        } catch (IOException e) {
            e.printStackTrace();
            mostrarMensajeError("Error", "No se pudo abrir el diálogo: " + e.getMessage());
        }
    }

    /**
     * Muestra una confirmación antes de eliminar un usuario, y realiza la eliminación si se aprueba.
     *
     * @param usuario Usuario a eliminar.
     */
    private boolean actualizarUsuario(UsuarioTablaModel usuario, boolean esEmpleado) {
        String tabla = esEmpleado ? "Empleado" : "Cliente";
        String query = "UPDATE " + tabla + " SET nombre = ?, email = ?, fotoPerfilUrl = ?, discordid = ?";

        if (esEmpleado) {
            query += ", rol = ?, salario = ?";
        }

        query += " WHERE id = ?";

        try (Connection conn = DBUtil.getConexion();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, usuario.getNombre());
            stmt.setString(2, usuario.getEmail());
            stmt.setString(3, usuario.getFotoPerfilUrl());

            // El ID de Discord es opcional
            if (usuario.getDiscordId() != null && !usuario.getDiscordId().isEmpty()) {
                stmt.setString(4, usuario.getDiscordId());
            } else {
                stmt.setNull(4, Types.VARCHAR);
            }

            if (esEmpleado) {
                stmt.setString(5, usuario.getRol());
                stmt.setDouble(6, usuario.getSalario());
                stmt.setInt(7, usuario.getId());
            } else {
                stmt.setInt(5, usuario.getId());
            }

            int filas = stmt.executeUpdate();
            return filas > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensajeError("Error de base de datos", "Error al actualizar el usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Elimina un usuario de la base de datos según su tipo (Empleado o Cliente).
     *
     * @param usuario Usuario a eliminar.
     * @return true si la eliminación fue exitosa, false en caso contrario.
     */
    private void confirmarEliminacion(UsuarioTablaModel usuario) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmar eliminación");
        alert.setHeaderText("¿Estás seguro de eliminar este usuario?");
        alert.setContentText("Nombre: " + usuario.getNombre() + "\nRol: " + usuario.getRol());

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (eliminarUsuario(usuario)) {
                listaUsuarios.remove(usuario);
                aplicarFiltros();
                mostrarMensajeInfo("Usuario eliminado", "El usuario ha sido eliminado con éxito.");
            }
        }
    }

    /**
     * Muestra una alerta de error con un mensaje personalizado.
     *
     * @param usuario
     */
    private boolean eliminarUsuario(UsuarioTablaModel usuario) {
        String tabla = usuario.isEsEmpleado() ? "Empleado" : "Cliente";
        String query = "DELETE FROM " + tabla + " WHERE id = ?";

        try (Connection conn = DBUtil.getConexion();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, usuario.getId());
            int filas = stmt.executeUpdate();
            return filas > 0;

        } catch (SQLException e) {
            e.printStackTrace();
            mostrarMensajeError("Error de base de datos", "Error al eliminar el usuario: " + e.getMessage());
            return false;
        }
    }

    /**
     * Muestra una alerta de error con un mensaje personalizado.
     *
     * @param titulo Título de la ventana.
     * @param mensaje Mensaje de error.
     */
    private void mostrarMensajeError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /**
     * Muestra una alerta informativa con un mensaje personalizado.
     *
     * @param titulo Título de la ventana.
     * @param mensaje Mensaje informativo.
     */
    private void mostrarMensajeInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(titulo);
        alert.setHeaderText(null);
        alert.setContentText(mensaje);
        alert.showAndWait();
    }

    /**
     * Modelo de datos utilizado para mostrar los usuarios en la tabla de administración.
     */
    public static class UsuarioTablaModel {
        private int id;
        private String nombre;
        private String email;
        private String rol;
        private double salario;
        private String fotoPerfilUrl;
        private boolean esEmpleado;
        private String discordId;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }

        public String getNombre() { return nombre; }
        public void setNombre(String nombre) { this.nombre = nombre; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getRol() { return rol; }
        public void setRol(String rol) { this.rol = rol; }

        public double getSalario() { return salario; }
        public void setSalario(double salario) { this.salario = salario; }

        public String getFotoPerfilUrl() { return fotoPerfilUrl; }
        public void setFotoPerfilUrl(String fotoPerfilUrl) { this.fotoPerfilUrl = fotoPerfilUrl; }

        public boolean isEsEmpleado() { return esEmpleado; }
        public void setEsEmpleado(boolean esEmpleado) { this.esEmpleado = esEmpleado; }

        public String getDiscordId() { return discordId; }
        public void setDiscordId(String discordId) { this.discordId = discordId; }
    }
}