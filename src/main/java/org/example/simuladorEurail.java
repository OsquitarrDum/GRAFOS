package org.example;

import com.sun.net.httpserver.HttpServer;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleDirectedWeightedGraph;
import org.jgrapht.traverse.BreadthFirstIterator;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class simuladorEurail {

    static final String DATASET_JSON = """
        {
          "nodos": ["Paris","London","Berlin","Rome","Madrid","Vienna","Amsterdam","Brussels","Munich","Prague","Budapest","Zurich","Milan","Geneva","Warsaw"],
          "rutas": [
            {"origen":"Paris","destino":"London","minutos":18},{"origen":"London","destino":"Berlin","minutos":14},
            {"origen":"Berlin","destino":"Rome","minutos":11},{"origen":"Rome","destino":"Madrid","minutos":13},
            {"origen":"Madrid","destino":"Vienna","minutos":12},{"origen":"Vienna","destino":"Amsterdam","minutos":6},
            {"origen":"Amsterdam","destino":"Brussels","minutos":8},{"origen":"Brussels","destino":"Munich","minutos":22},
            {"origen":"Munich","destino":"Prague","minutos":23},{"origen":"Prague","destino":"Budapest","minutos":21},
            {"origen":"Budapest","destino":"Zurich","minutos":19},{"origen":"Zurich","destino":"Milan","minutos":23},
            {"origen":"Milan","destino":"Geneva","minutos":9},{"origen":"Geneva","destino":"Warsaw","minutos":24},
            {"origen":"Paris","destino":"Madrid","minutos":19},{"origen":"Rome","destino":"Brussels","minutos":23},
            {"origen":"Amsterdam","destino":"Budapest","minutos":35},{"origen":"Prague","destino":"Geneva","minutos":40},
            {"origen":"Berlin","destino":"Milan","minutos":36},{"origen":"London","destino":"Warsaw","minutos":22}
          ]
        }""";

    public static void main(String[] args) throws Exception {
        // 1. Instanciar Grafo
        Graph<String, DefaultWeightedEdge> grafo =
                new SimpleDirectedWeightedGraph<>(DefaultWeightedEdge.class);

        // 2. Cargar las 15 ciudades del JSON Eurail
        String[] ciudades = {
                "Paris", "London", "Berlin", "Rome", "Madrid",
                "Vienna", "Amsterdam", "Brussels", "Munich", "Prague",
                "Budapest", "Zurich", "Milan", "Geneva", "Warsaw"
        };
        for (String c : ciudades) grafo.addVertex(c);

        // 3. Cargar las 20 rutas exactas y sus minutos
        agregarRuta(grafo, "Paris", "London", 18);
        agregarRuta(grafo, "London", "Berlin", 14);
        agregarRuta(grafo, "Berlin", "Rome", 11);
        agregarRuta(grafo, "Rome", "Madrid", 13);
        agregarRuta(grafo, "Madrid", "Vienna", 12);
        agregarRuta(grafo, "Vienna", "Amsterdam", 6);
        agregarRuta(grafo, "Amsterdam", "Brussels", 8);
        agregarRuta(grafo, "Brussels", "Munich", 22);
        agregarRuta(grafo, "Munich", "Prague", 23);
        agregarRuta(grafo, "Prague", "Budapest", 21);
        agregarRuta(grafo, "Budapest", "Zurich", 19);
        agregarRuta(grafo, "Zurich", "Milan", 23);
        agregarRuta(grafo, "Milan", "Geneva", 9);
        agregarRuta(grafo, "Geneva", "Warsaw", 24);
        agregarRuta(grafo, "Paris", "Madrid", 19);
        agregarRuta(grafo, "Rome", "Brussels", 23);
        agregarRuta(grafo, "Amsterdam", "Budapest", 35);
        agregarRuta(grafo, "Prague", "Geneva", 40);
        agregarRuta(grafo, "Berlin", "Milan", 36);
        agregarRuta(grafo, "London", "Warsaw", 22);

        // 4. Prueba directa en consola (Dijkstra + BFS)
        ejecutarDijkstra(grafo, "Paris", "Budapest");
        ejecutarBFS(grafo, "Paris", 2);

        // 5. Servidor local para conectar con index.html en http://localhost:8080
        iniciarServidorWeb(grafo);
    }

    static void agregarRuta(Graph<String, DefaultWeightedEdge> g, String origen, String destino, double min) {
        DefaultWeightedEdge e = g.addEdge(origen, destino);
        g.setEdgeWeight(e, min);
    }

    // MÉTODO 1: Ruta Óptima (Dijkstra)
    static String ejecutarDijkstra(Graph<String, DefaultWeightedEdge> grafo, String origen, String destino) {
        DijkstraShortestPath<String, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(grafo);
        GraphPath<String, DefaultWeightedEdge> camino = dijkstra.getPath(origen, destino);

        if (camino == null) {
            System.out.println("No hay conexión disponible entre " + origen + " y " + destino);
            return "{\"error\":\"No hay conexión disponible.\"}";
        }

        List<String> nodos = camino.getVertexList();
        int total = (int) camino.getWeight();
        System.out.println("Ruta Óptima (" + origen + " -> " + destino + "): " + nodos + " | " + total + " min");

        // Construir JSON para el frontend
        List<String> tramos = new ArrayList<>();
        for (int i = 0; i < nodos.size() - 1; i++) {
            int min = (int) grafo.getEdgeWeight(grafo.getEdge(nodos.get(i), nodos.get(i + 1)));
            tramos.add(String.format("{\"desde\":\"%s\",\"hasta\":\"%s\",\"minutos\":%d}", nodos.get(i), nodos.get(i + 1), min));
        }
        String nodosJson = "[\"" + String.join("\",\"", nodos) + "\"]";
        return String.format("{\"tiempoTotal\":%d,\"saltos\":%d,\"camino\":%s,\"tramos\":[%s]}",
                total, tramos.size(), nodosJson, String.join(",", tramos));
    }

    // MÉTODO 2: Zonas de Influencia (BFS por profundidad/saltos)
    static String ejecutarBFS(Graph<String, DefaultWeightedEdge> grafo, String origen, int maxSaltos) {
        BreadthFirstIterator<String, DefaultWeightedEdge> bfs = new BreadthFirstIterator<>(grafo, origen);
        Map<Integer, List<String>> zonas = new LinkedHashMap<>();
        int totalCiudades = 0;

        while (bfs.hasNext()) {
            String ciudad = bfs.next();
            int nivel = bfs.getDepth(ciudad);
            if (nivel <= maxSaltos) {
                zonas.computeIfAbsent(nivel, k -> new ArrayList<>()).add(ciudad);
                totalCiudades++;
            }
        }
        System.out.println("Zonas BFS desde " + origen + " (máx " + maxSaltos + " saltos): " + zonas);

        List<String> entradasJson = new ArrayList<>();
        zonas.forEach((nivel, lista) ->
                entradasJson.add("\"" + nivel + "\":[\"" + String.join("\",\"", lista) + "\"]"));
        return String.format("{\"totalCiudades\":%d,\"zonas\":{%s}}", totalCiudades, String.join(",", entradasJson));
    }

    // Conexión ligera con src/main/resources/index.html
    static void iniciarServidorWeb(Graph<String, DefaultWeightedEdge> grafo) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", ex -> {
            byte[] html = simuladorEurail.class.getResourceAsStream("/index.html").readAllBytes();
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=UTF-8");
            ex.sendResponseHeaders(200, html.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(html); }
        });

        server.createContext("/api/grafo", ex -> {
            try {
                responderJson(ex, DATASET_JSON);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        server.createContext("/api/dijkstra", ex -> {
            Map<String, String> q = queryParams(ex.getRequestURI().getQuery());
            try {
                responderJson(ex, ejecutarDijkstra(grafo, q.get("origen"), q.get("destino")));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        server.createContext("/api/bfs", ex -> {
            Map<String, String> q = queryParams(ex.getRequestURI().getQuery());
            try {
                responderJson(ex, ejecutarBFS(grafo, q.get("origen"), Integer.parseInt(q.get("saltos"))));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        server.start();
        System.out.println("Simulador listo en http://localhost:8080");
    }

    static void responderJson(com.sun.net.httpserver.HttpExchange ex, String json) throws Exception {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        ex.sendResponseHeaders(200, data.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(data); }
    }

    static Map<String, String> queryParams(String query) {
        Map<String, String> map = new HashMap<>();
        if (query != null) {
            for (String p : query.split("&")) {
                String[] kv = p.split("=");
                if (kv.length == 2) map.put(kv[0], kv[1]);
            }
        }
        return map;
    }
}