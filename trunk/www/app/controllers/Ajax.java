package controllers;

import java.util.LinkedList;
import java.util.List;

import models.Project;
import models.User;

public class Ajax extends SmartController {
	/**
	 * renderJSON projects that match the query search term, and based on the
	 * boolean flag chosen to select from
	 * 
	 * @param query
	 *            search query
	 * @param invite
	 *            if true, selects projects this user canSendInvite to
	 */
	public static void projects(String query, boolean invite) {
		if (invite) {
			User me = Security.getConnected();
			List<Project> myProjects = new LinkedList<Project>();
			if (me.isAdmin)
				myProjects = Project.find("byNameLikeAndDeleted", "%" + query + "%", false).fetch();
			else
				for (Project project : me.projects) {
					if (project.name.contains(query) && me.in(project).can("invite"))
						myProjects.add(project);
				}
			List<Project.Object> result = new LinkedList<Project.Object>();
			for (Project p : myProjects) {
				result.add(new Project.Object(p.id, p.name));
			}
			renderJSON(result);
		} else {
			List<Project.Object> result = new LinkedList<Project.Object>();
			for (Project u : Project.find("byNameLikeAndDeleted", "%" + query + "%", false).<Project> fetch()) {
				result.add(new Project.Object(u.id, u.name));
			}
			renderJSON(result);
		}
		forbidden();
	}

	/**
	 * renderJSON users that match the search query
	 * 
	 * @param query
	 *            search query to search for
	 */
	public static void users(String query) {
		List<User.Object> result = new LinkedList<User.Object>();
		for (User u : User.find("byNameLikeAndDeleted", "%" + query + "%", false).<User> fetch()) {
			result.add(new User.Object(u.id, u.name));
		}
		renderJSON(result);
	}
}