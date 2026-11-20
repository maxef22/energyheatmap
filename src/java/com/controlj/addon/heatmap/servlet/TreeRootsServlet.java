package com.controlj.addon.heatmap.servlet;

import com.controlj.green.addonsupport.access.*;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

@WebServlet("/tree/roots")
public class TreeRootsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            SystemConnection conn = DirectAccess.getDirectAccess().getRootSystemConnection();
            final List<JSONObject> roots = new ArrayList<>();

            conn.runReadAction(new ReadAction() {
                @Override
                public void execute(SystemAccess access) throws Exception {
                    Tree geo = access.getTree(SystemTree.Geographic);
                    Location root = geo.getRoot();
                    for (Location child : root.getChildren()) {
                        JSONObject obj = new JSONObject();
                        // Отображаемое имя (человекочитаемое)
                        String displayName = child.getDisplayName();
                        if (displayName == null || displayName.trim().isEmpty()) {
                            displayName = child.getReferenceName();
                        }
                        obj.put("displayName", displayName);
                        // Технический путь для поиска
                        String fullPath = child.toString();
                        int hashIndex = fullPath.indexOf('#');
                        if (hashIndex != -1) {
                            fullPath = fullPath.substring(hashIndex);
                        }
                        obj.put("path", fullPath);
                        roots.add(obj);
                    }
                }
            });

            JSONArray result = new JSONArray(roots);
            resp.setContentType("application/json; charset=UTF-8");
            resp.setCharacterEncoding("UTF-8");
            PrintWriter out = resp.getWriter();
            out.print(result.toString());
        } catch (Exception e) {
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.getWriter().print("{\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }
}