package controllers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import models.Component;
import models.Project;
import models.Sprint;
import models.Story;
import models.Task;
import models.User;
import play.db.jpa.JPASupport;
import play.exceptions.TemplateNotFoundException;
import play.i18n.Messages;
import play.mvc.With;

@With (Secure.class)
public class Storys extends SmartCRUD {
	/**
	 * Override the default CRUD blank method to get the stories in a project,
	 * the project's components and the project priorities sorted instead of
	 * just listing everything.
	 * 
	 * @param id
	 *            Project id to create the story into.
	 * @author Galal Aly
	 * @return void
	 **/
	// @Check ("canAddStory")
	public static void blank(long id) {
		boolean roles, components, priorities = true;
		roles = components = priorities;
		ObjectType type = ObjectType.get(getControllerClass());
		notFoundIfNull(type);
		// Whether everything is ok to create the story
		boolean ok = true;
		// We will add the story to a project .. We need to get that project
		Project project = Project.findById(id);
		User user = Security.getConnected();
		Security.check(user.in(project).can("addStory"));
		if (project == null)
			ok = false;
		if (project.productRoles == null) {
			ok = false;
			roles = false;
		}
		// We can set the dependent stories .. We need to get a list of stories
		// in a project to list them so that we can set the dependency
		ArrayList<Story> stories = new ArrayList<Story>();
		// For each component in a project
		for (Component component : project.components) {
			// For every story
			for (Story story : component.componentStories) {
				// Add the story to the list
				stories.add(story);
			}
		}
		if (project.priorities == null || project.priorities.isEmpty()) {
			// ok = false;
			// priorities = false;
		}
		if (!ok)
			render("Storys/error.html", priorities, components, roles);
		// Sort the priorities according to their priority
		// Collections.sort(project.priorities);
		try {
			render(type, stories, project);
		} catch (TemplateNotFoundException e) {
			render("CRUD/blank.html", type);
		}
	}

	/**
	 * Override the default CRUD show method to get the stories in a project,
	 * the project's components and the project priorities sorted instead of
	 * just listing everything. It also checks whether the story is editable or
	 * not.
	 * 
	 * @param id
	 *            Story id to be edited
	 * @author Galal Aly
	 * @return void
	 **/
	// @Check ("canEditStory")
	public static void show(String id) {
		boolean roles, components, priorities = true;
		roles = components = priorities;
		ObjectType type = ObjectType.get(getControllerClass());
		notFoundIfNull(type);
		JPASupport object = type.findById(id);
		// We added the story to a project .. We need to get that project
		Story temp = (Story) object;
		if (temp.productRole != null) {
			int formatt = 13 + temp.productRole.name.length();
			temp.description = temp.description.substring(formatt);
		}
		// Whether everything is ok to edit the story
		boolean ok = true;
		Project project = temp.componentID.project;
		User user = Security.getConnected();
		Security.check(user.in(project).can("editStory"));
		if (project == null)
			ok = false;
		if (project.productRoles == null) {
			ok = false;
			roles = false;
		}
		// We can set the dependent stories .. We need to get a list of stories
		// in a project to list them so that we can set the dependency
		ArrayList<Story> stories = new ArrayList<Story>();
		// For each component in a project
		if (project.components == null) {
			ok = false;
			components = false;
		}
		for (Component component : project.components) {
			// For every story
			for (Story story : component.componentStories) {
				// Add the story to the list
				stories.add(story);
			}
		}
		// Sort the priorities according to their priority (how priorities are
		// compared is specified in the model Priority.java)
		if (project.priorities == null || project.priorities.isEmpty()) {
			// ok = false;
			priorities = false;
		}
		// Collections.sort(project.priorities);

		if (!ok)
			render("Storys/error.html", priorities, components, roles);

		// Is the story editable?!
		boolean editable = !(temp.inSprint());

		String message = "This can not be deleted";
		boolean editable2 = true;
		if (editable) {
			if (temp.hasDependency()) {
				editable = false;
				editable2 = false;
			} else {
				message = "Are you sure you want to delete this story? This action can not be undone.";
			}
		}
		try {
			render(type, object, stories, project, editable, editable2, message);
		} catch (TemplateNotFoundException e) {
			render("CRUD/show.html", type, object);
		}
	}

	/**
	 * Override the default CRUD delete method in order to follow the company's
	 * convention by setting a boolean variable to true when deleting something.
	 * The process of deleting a story : 1- Remove all dependencies on this
	 * story. 2- Delete all tasks in this story.
	 * 
	 * @param id
	 *            Story id to be deleted
	 * @author Galal Aly
	 * @return void
	 **/
	// @Check ("canDeleteStory")
	public static void delete(String id) {
		ObjectType type = ObjectType.get(getControllerClass());
		notFoundIfNull(type);
		JPASupport object = type.findById(id);
		Story story = (Story) object;
		try {
			/*
			 * Plan : - Loop on all stories that depends on this story and
			 * remove the dependency - Mark all the tasks under this story as
			 * deleted
			 */
			ArrayList<Story> projectStories = new ArrayList<Story>();
			Project project = story.componentID.project;
			User user = Security.getConnected();
			Security.check(user.in(project).can("deleteStory"));
			for (Component component : project.components) {
				projectStories.addAll(component.componentStories);
			}
			for (Story x : projectStories) {
				int temp = x.dependsOn(story);
				if (temp != -1)
					x.dependentStories.remove(temp);
				x.save();
			}

			for (Task t : story.storiesTask) {
				// delete the tasks
				t.DeleteTask();
			}
			String header = "Story: 'S" + story.id + "\'" + " has been deleted.";
			String body = "In Project: " + "\'" + project.name + "\'" + "." + '\n' + " In Component: " + "\'" + story.componentID.name + "\'" + "." + '\n' + " Deleted by: " + "\'" + Security.getConnected().name + "\'" + ".";
			/*
			 * ////Long Informative Notification message. Not suitable for
			 * online notification. String header = "A Story in Component: " +
			 * "\'" + story.componentID.name + "\'" + " in Project: " + "\'" +
			 * project.name + "\'" + " has been deleted."; String body =
			 * "The Story:" + '\n' + " " + "\'" + story.description + "\'" +
			 * '\n' + " in Component: " + "\'" + story.componentID.name + "\'" +
			 * " in Project: " + "\'" + project.name + "\'" +
			 * " has been deleted." + '\n' + '\n' + " Deleted by: " +
			 * story.addedBy.name + "."; // Now mark my story as deleted
			 */
			story.deleted = true;
			story.save();
			Logs.addLog(story.addedBy, "Delete", "Story", story.id, project, new Date(System.currentTimeMillis()));
			Notifications.notifyUsers(story.componentID.getUsers(), header, body, (byte) -1);
		} catch (Exception e) {
			System.out.println(e);
			flash.error(Messages.get("crud.delete.error", type.modelName, object.getEntityId()));
			redirect(request.controller + ".show", object.getEntityId());
		}
		flash.success(Messages.get("crud.deleted", type.modelName, object.getEntityId()));
		listStoriesInProject(story.componentID.project.id, 0);
	}

	/**
	 * Override the default CRUD create method to get the stories in a project,
	 * the project's components and the project priorities sorted instead of
	 * just listing everything and save the new story in the database.
	 * 
	 * @author Galal Aly
	 * @return void
	 **/
	// @Check ("canAddStory")
	public static void create() throws Exception {
		ObjectType type = ObjectType.get(getControllerClass());
		notFoundIfNull(type);
		JPASupport object = type.entityClass.newInstance();
		validation.valid(object.edit("object", params));
		// We will add the story to a project .. We need to get that project
		Story storyObj = (Story) object;
		Project project = storyObj.componentID.project;
		User user = Security.getConnected();
		Security.check(user.in(project).can("addStory"));
		validation.valid(object.edit("object", params));

		// We can set the dependent stories .. We need to get a list of stories
		// in a project to list them so that we can set the dependency
		ArrayList<Story> stories = new ArrayList<Story>();
		// For each component in a project
		for (Component component : project.components) {
			// For every story
			for (Story story : component.componentStories) {
				// Add the story to the list
				stories.add(story);
			}
		}
		// Sort the priorities according to their priority
		Collections.sort(project.priorities);
		if (validation.hasErrors()) {
			renderArgs.put("error", Messages.get("crud.hasErrors"));
			try {
				render(request.controller.replace(".", "/") + "/blank.html", type, stories, project);
			} catch (TemplateNotFoundException e) {
				render("CRUD/blank.html", type);
			}
		}
		Story toBeSaved = (Story) object;
		storyObj.description = "As a " + storyObj.productRole.name + ", I can " + storyObj.description;
		toBeSaved.addedBy = Security.getConnected();
		object.save();
		Logs.addLog(toBeSaved.addedBy, "Create", "Story", storyObj.id, project, new Date(System.currentTimeMillis()));
		String header = "A new Story has been added.";
		String body = "In Project: " + "\'" + project.name + "\'" + "." + '\n' + " In Component: " + "\'" + storyObj.componentID.name + "\'" + "." + '\n' + " Story: 'S" + storyObj.id + "\'" + "." + '\n' + " Added by: " + "\'" + toBeSaved.addedBy.name + "\'" + ".";
		/*
		 * ////Long Informative Notification message. Not suitable for online
		 * notification. String header =
		 * "New Story has been added to Component: " + "\'" +
		 * storyObj.componentID.name + "\'" + " in Project: " + "\'" +
		 * project.name + "\'" + "."; String body =
		 * "New Story has been added to Component: " + "\'" +
		 * storyObj.componentID.name + "\'" + " in Project: " + "\'" +
		 * project.name + "\'" + "." + '\n' + '\n' + "Story Description: " +
		 * storyObj.description + "." + '\n' + " Priority: " + storyObj.priority
		 * + "." + '\n' + " Estimate points: " + storyObj.estimate + "." + '\n'
		 * + " Succuss Senario: " + storyObj.succussSenario + "." + '\n' +
		 * " Failure Senario: " + storyObj.failureSenario + "." + '\n' +
		 * " Notes: " + storyObj.notes + "." + '\n' + " Added by: " +
		 * toBeSaved.addedBy.name + ".";
		 */
		Notifications.notifyUsers(storyObj.componentID.getUsers(), header, body, (byte) 1);
		flash.success(Messages.get("crud.created", type.modelName, object.getEntityId()));
		if (params.get("_save") != null) {
			listStoriesInProject(project.id, 0);
		}
		if (params.get("_saveAndAddAnother") != null) {
			redirect(request.controller + ".blank");
		}
		redirect(request.controller + ".show", object.getEntityId());
	}

	/**
	 * Override the default CRUD save method to get the stories in a project,
	 * the project's components and the project priorities sorted instead of
	 * just listing everything and render them to the page after saving the
	 * element in the database. Saves the element in the database.
	 * 
	 * @param id
	 *            Project id to create the story into.
	 * @author Galal Aly
	 * @return void
	 **/
	// @Check ("canEditStory")
	public static void save(String id) throws Exception {
		ObjectType type = ObjectType.get(getControllerClass());
		notFoundIfNull(type);
		JPASupport object = type.findById(id);
		validation.valid(object.edit("object", params));
		Story storyObj = (Story) object;
		String oldDescription = storyObj.description;
		storyObj.description = "As a " + storyObj.productRole.name + ", I can " + storyObj.description;
		// We will add the story to a project .. We need to get that project

		Project project = storyObj.componentID.project;
		User user = Security.getConnected();
		Security.check(user.in(project).can("editStory"));
		validation.valid(object.edit("object", params));
		// We can set the dependent stories .. We need to get a list of stories
		// in a project to list them so that we can set the dependency
		ArrayList<Story> stories = new ArrayList<Story>();
		// For each component in a project
		for (Component component : project.components) {
			// For every story
			for (Story story : component.componentStories) {
				// Add the story to the list
				stories.add(story);
			}
		}
		// Sort the priorities according to their priority
		Collections.sort(project.priorities);
		if (validation.hasErrors()) {
			renderArgs.put("error", Messages.get("crud.hasErrors"));
			try {
				render(request.controller.replace(".", "/") + "/show.html", type, object, project, stories);
			} catch (TemplateNotFoundException e) {
				render("CRUD/show.html", type, object);
			}
		}
		storyObj.description = "As a " + storyObj.productRole.name + ", I can " + storyObj.description;
		String header = "Story: 'S" + storyObj.id + "\'" + " has been edited.";
		String body = "In Project: " + "\'" + project.name + "\'" + "." + '\n' + " In Component: " + "\'" + storyObj.componentID.name + "\'" + "." + '\n' + " Edited by: " + "\'" + Security.getConnected().name + "\'" + ".";
		/*
		 * ////Long Informative Notification message. Not suitable for online
		 * notification. String header =
		 * "A Story has been edited in Component: " + "\'" +
		 * storyObj.componentID.name + "\'" + " in Project: " + "\'" +
		 * project.name + "\'" + "."; String body = "The Story:" + '\n' + " " +
		 * "\'" + oldDescription + "\'" + '\n' +
		 * " has been edited in Component: " + "\'" + storyObj.componentID.name
		 * + "\'" + " in Project: " + "\'" + project.name + "\'" + "." + '\n' +
		 * '\n' + "Story Description: " + storyObj.description + "." + '\n' +
		 * " Priority: " + storyObj.priority + "." + '\n' + " Estimate points: "
		 * + storyObj.estimate + "." + '\n' + " Succuss Senario: " +
		 * storyObj.succussSenario + "." + '\n' + " Failure Senario: " +
		 * storyObj.failureSenario + "." + '\n' + " Notes: " + storyObj.notes +
		 * "." + '\n' + " Added by: " + storyObj.addedBy.name + ".";
		 */
		object.save();
		/**********
		 * Log and notification
		 */
		Logs.addLog(storyObj.addedBy, "Edit", "Story", storyObj.id, project, new Date(System.currentTimeMillis()));
		Notifications.notifyUsers(storyObj.componentID.getUsers(), header, body, (byte) 0);
		flash.success(Messages.get("crud.saved", type.modelName, object.getEntityId()));
		if (params.get("_save") != null) {
			listStoriesInProject(project.id, 0);
		}
		redirect(request.controller + ".show", object.getEntityId());
	}

	/**
	 * Edits a description of a certain story.
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param desc
	 *            The new description
	 * @author Galal Aly
	 * @return void
	 **/
	public static void editDes(long id, String desc) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		s.description = desc;
		s.save();
	}

	/**
	 * Edits success scenarios of a certain story.
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param succ
	 *            The new success scenario
	 * @author Galal Aly
	 * @return void
	 **/

	public static void editSuccess(long id, String succ) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		s.succussSenario = succ;
		s.save();
	}

	/**
	 * Edits failure scenarios of a certain story.
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param fail
	 *            The new failure scenarios
	 * @author Galal Aly
	 * @return void
	 **/

	public static void editFailure(long id, String fail) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		s.failureSenario = fail;
		s.save();
	}

	/**
	 * Edits notes of a certain story.
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param notes
	 *            The new notes
	 * @author Galal Aly
	 * @return void
	 **/

	public static void editNotes(long id, String n) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		s.notes = n;
		s.save();
	}

	/**
	 * Changes the component of a story
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param c
	 *            The new component
	 * @author Galal Aly
	 * @return void
	 **/

	public static void editComponent(long id, Component c) {
		// seen so2al
		// ezay ba3etly Component? o.O
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory") && s.componentID.project == c.project);
		s.componentID = c;
		s.save();
	}

	/**
	 * Changes the priority of a certain story
	 * 
	 * @param id
	 *            Story id to be edited
	 * @param desc
	 *            The new priority
	 * @author Galal Aly
	 * @return void
	 **/

	public static void editPriority(long id, int p) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		s.priority = p;
		s.save();
	}

	/**
	 * Add or remove a story from the list of dependent stories of a story
	 * 
	 * @param storyId
	 *            Story id to be edited
	 * @param toBeAddedOrRemoved
	 *            The input story to be added or removed
	 * @param remove
	 *            Whether to remove or add the story (false add)
	 * @author Galal Aly
	 * @return void
	 **/

	public static void addRemoveDependentStories(long storyId, Story toBeAddedOrRemoved, boolean remove) {
		Story s = Story.findById(storyId);
		Security.check(Security.getConnected().in(s.componentID.project).can("editStory"));
		if (remove) {
			s.dependentStories.remove(s.dependentStories.indexOf(toBeAddedOrRemoved));
		} else {
			s.dependentStories.add(toBeAddedOrRemoved);
		}
	}

	/**
	 * Edits dependent Stories of a certain story.
	 * 
	 * @param storyId
	 *            Story id to be edited
	 * @param newList
	 *            The new list of dependent Stories
	 * @author Galal Aly
	 * @return void
	 **/

	public static boolean editDependentStories(long storyId, ArrayList<Story> newList) {
		Story s = Story.findById(storyId);
		// no security checks to perform here, this method is not accessible via
		// usual requests
		s.dependentStories = newList;
		s.save();
		return true;
	}

	public static boolean fromIdToStory(long storyId, String[] idList) {
		ArrayList<Story> newList = new ArrayList<Story>();
		long id;
		if (idList == null)
			return false;

		for (int i = 0; i < idList.length; i++) {
			id = Long.parseLong(idList[i]);
			Story story = Story.findById(id);
			newList.add(story);
		}

		editDependentStories(storyId, newList);
		return true;

	}

	/**
	 * @author menna_ghoneim Renders a give story id to a page to choose the
	 *         dependent stories
	 * @param id
	 *            the story to be edited
	 */
	public static void chooseDependentStories(long id) {
		Story s = Story.findById(id);
		Security.check(Security.getConnected().in(s.componentID.project).can("setDependentStories"));
		List<Story> storys = new ArrayList<Story>();

		for (Component comp : s.componentID.project.components) {
			storys.addAll(comp.componentStories);
		}
		storys.remove(s);
		List<String> sIds = new ArrayList<String>();

		for (int i = 0; i < storys.size(); i++) {
			sIds.add(storys.get(i).id + "");
		}

		render(id, storys, sIds);
	}

	/**
	 * view the assign Story to the Sprint.
	 * 
	 * @author hoksha
	 * @param void
	 * @return void
	 * @task C3 S12
	 * @sprint 2
	 */

	// @Check ("canassignStorytoSprint")
	public static void listStoriesandSprints(long PID) {
		Date todayDate = new GregorianCalendar().getTime();
		Project project = Project.findById(PID);
		// System.out.println(project);
		User user = Security.getConnected();
		Security.check(user.in(project).can("assignStoryToSprint"));
		ArrayList<Story> stories = new ArrayList<Story>();
		List<Component> components = project.components;

		for (int i = 0; i < components.size(); i++) {
			for (int j = 0; j < components.get(i).componentStories.size(); j++) {
				stories.add(components.get(i).componentStories.get(j));
			}
		}

		List<Sprint> sprints = project.sprints;
		if (stories != null) {
			for (int i = 0; i < sprints.size(); i++) {
				if (sprints.get(i).deleted == true) {
					sprints.remove(i);
				}
				if (sprints.get(i).startDate.before(todayDate)) {
					sprints.remove(i);
				}
			}
			for (int i = 0; i < components.size(); i++) {
				for (int j = 0; j < components.get(i).componentStories.size(); j++) {
					stories.add(components.get(i).componentStories.get(j));
				}
			}

			render(stories, sprints);
		}
	}

	/**
	 * assign Story to a sprint.
	 * 
	 * @author hoksha
	 * @param storyID
	 *            id of the story
	 * @param sprintID
	 *            id of the sprint
	 * @return void
	 * @task C3,S12
	 * @Sprint 2
	 */
	public static void assignStoryToSprint(long storyID, long sprintID) {
		String message = "";
		Story story = Story.findById(storyID);
		Sprint sprint = Sprint.findById(sprintID);
		User user = Security.getConnected();
		Security.check(user.in(story.componentID.project).can("assignStoryToSprint") && user.in(sprint.project).can("assignStoryToSprint"));

		if (story == null) {
			message = "invalid story ID";

		} else {
			if (story.storiesTask == null) {
				message = "no tasks  inside this story";
			} else {
				if (sprint == null) {
					message = "invalid sprint ID";
				} else {
					if (!story.storiesTask.isEmpty()) {
						if (story.storiesTask.get(0).taskSprint != null) {
							message = "story is already assignend to a sprint";
						} else {
							for (int i = 0; i < story.storiesTask.size(); i++) {

								story.storiesTask.get(i).taskSprint.equals(sprint);
								story.storiesTask.get(i).save();
								story.storiesTask.get(i).taskSprint.save();

							}
							for (int i = 0; i < story.storiesTask.size(); i++) {
								sprint.tasks.add(story.storiesTask.get(i));
								sprint.tasks.get(i).save();
							}
							message = "story is assigned to the sprint";

						}
					} else {
						message = "this story contains no tasks ";
					}

				}
			}

		}
		String header = "Story 'S" + story.id + "\'" + " has been assigned to Sprint: " + "\'" + sprint.sprintNumber + "\'" + ".";
		String body = "In Project: " + "\'" + story.componentID.name + "\'" + "." + '\n' + " In Component: " + "\'" + sprint.project.name + "\'" + "." + '\n' + " Assigned by: " + "\'" + Security.getConnected().name + "\'" + ".";
		/*
		 * ////Long Informative Notification message. Not suitable for online
		 * notification. String header =
		 * "New Story has been assigned to Sprint: " + "\'" +
		 * sprint.sprintNumber + "\'" + " in Project: " + "\'" +
		 * sprint.project.name + "\'" + "."; String body = "The Story: " + "\'"
		 * + story.description + "\'" + '\n' + " has been assigned to Sprint: "
		 * + "\'" + sprint.sprintNumber + "\'" + " in Project: " + "\'" +
		 * sprint.project.name + "\'" + "."+ '\n' + '\n' + " Assigned by: " +
		 * "\'" + Security.getConnected().name + "\'" + ".";
		 */
		Notifications.notifyUsers(sprint.project, header, body, "assignStoryToSprint", new Byte((byte) 0));
		Logs.addLog(Security.getConnected(), "Assign", "Story To Sprint", story.id, sprint.project, new Date(System.currentTimeMillis()));
		story.save();
		sprint.save();
		renderText(message);
	}

	/**
	 * gets the succsses and failure scianrios of the given story
	 * 
	 * @author Moumen Mohamed story=C3S16
	 * @param id
	 *            the id of the story edited
	 * @return void
	 */
	// @Check ("canEditStory")
	public static void editScenario(long id) {
		Story story1 = Story.findById(id);
		ArrayList<String> succsses = new ArrayList();
		ArrayList<String> failure = new ArrayList();
		Project project = story1.componentID.project;
		User user = Security.getConnected();
		Security.check(user.in(project).can("editStory"));
		if (story1.succussSenario != null) {
			String[] s = story1.succussSenario.split("\n");
			for (int i = 0; i < s.length; i++) {
				succsses.add(s[i]);
			}
		}
		if (story1.failureSenario != null) {
			String[] f = story1.failureSenario.split("\n");
			for (int i = 0; i < f.length; i++) {
				failure.add(f[i]);
			}
		}

		if (story1.succussSenario == null)
			succsses.add("");
		if (story1.failureSenario == null)
			failure.add("");

		int succssesNum = succsses.size();
		int failureNum = failure.size();
		long storyId = id;
		Long projectId = story1.componentID.project.id;
		render(succsses, failure, succssesNum, failureNum, storyId, projectId);

	}

	/**
	 * saves the succsses scinario of the given story
	 * 
	 * @author Moumen Mohamed story=C3S16
	 * @param id
	 *            the id of the story edited
	 * @param f
	 *            the string of new succsses scenarios
	 * @return void
	 */
	public static void saveSuccsses(long id, String s) {
		Story story1 = Story.findById(id);
		Security.check(Security.getConnected().in(story1.componentID.project).can("editStory"));
		story1.succussSenario = s;
		story1.save();
		Logs.addLog(Security.getConnected(), "Create", "Succuss Senario", story1.id, story1.componentID.project, new Date(System.currentTimeMillis()));
		String header = "A new Succuss Senario has been added to Story 'S" + story1.id + "\'" + ".";
		String body = "In Project: " + "\'" + story1.componentID.project.name + "\'" + "." + '\n' + " In Component: " + "\'" + story1.componentID.name + "\'" + "." + '\n' + " Added by: " + "\'" + Security.getConnected().name + "\'" + ".";
		/*
		 * ////Long Informative Notification message. Not suitable for online
		 * notification. String header =
		 * "New Succuss Senario has been added to a Story in Component: " + "\'"
		 * + story1.componentID.name + "\'" + " in Project: " + "\'" +
		 * story1.componentID.project.name + "\'" + "."; String body =
		 * "New Succuss Senario has been added to a Story: " + "\'" +
		 * story1.description + "\'" + " in Component: " + "\'" +
		 * story1.componentID.name + "\'" + " in Project: " + "\'" +
		 * story1.componentID.project.name + "\'" + "." + '\n' + '\n' +
		 * "Succuss Senario: " + story1.succussSenario + "." + '\n' +
		 * " Added by: " + "\'" + Security.getConnected().name + "\'" + ".";
		 */
		Notifications.notifyUsers(story1.componentID.getUsers(), header, body, (byte) 0);
	}

	/**
	 * saves the failure scinario of the given story
	 * 
	 * @author Moumen Mohamed story=C3S16
	 * @param id
	 *            the id of the story edited
	 * @param f
	 *            the string of new failure scenarios
	 * @return void
	 */
	public static void saveFailure(long id, String f) {
		Story story1 = Story.findById(id);
		Security.check(Security.getConnected().in(story1.componentID.project).can("editStory"));
		story1.failureSenario = f;
		story1.save();
		Logs.addLog(Security.getConnected(), "Create", "Failure Senario", story1.id, story1.componentID.project, new Date(System.currentTimeMillis()));
		String header = "A new Failure Senario has been added to Story 'S" + story1.id + "\'" + ".";
		String body = "In Project: " + "\'" + story1.componentID.project.name + "\'" + "." + '\n' + " In Component: " + "\'" + story1.componentID.name + "\'" + "." + '\n' + " Added by: " + "\'" + Security.getConnected().name + "\'" + ".";
		/*
		 * ////Long Informative Notification message. Not suitable for online
		 * notification. String header =
		 * "New Failure Senario has been added to a Story in Component: " + "\'"
		 * + story1.componentID.name + "\'" + " in Project: " + "\'" +
		 * story1.componentID.project.name + "\'" + "."; String body =
		 * "New Failure Senario has been added to a Story: " + "\'" +
		 * story1.description + "\'" + " in Component: " + "\'" +
		 * story1.componentID.name + "\'" + " in Project: " + "\'" +
		 * story1.componentID.project.name + "\'" + "." + '\n' + '\n' +
		 * "Failure Senario: " + story1.failureSenario + "." + '\n' +
		 * " Added by: " + "\'" + Security.getConnected().name + "\'" + ".";
		 */
		Notifications.notifyUsers(story1.componentID.getUsers(), header, body, (byte) 0);
	}

	public static void listStoriesInProject(long projectId, long storyId) {
		long cId = 0;
		Story myStory = Story.findById(storyId);
		if (myStory != null)
			cId = myStory.componentID.id;
		boolean ok = true;
		boolean noStories = true;
		String message = "";
		Project project = Project.findById(projectId);
		if (project == null) {
			ok = false;
			message += "<li>No project found with this id</li>";
		}
		// For each component in a project
		if (project.components == null) {
			ok = false;
			message += "<li>No components in this project and therefore, hopefully, no stories</li>";
		} else {
			// Check that there is at least one story in the project
			for (Component component : project.components) {
				if (component.componentStories == null || component.componentStories.isEmpty())
					continue;
				else {
					noStories = false;
					break;
				}
			}
		}
		if (noStories) {
			ok = false;
			message += "<p>No Stories in this project</p>";
		}
		if (!ok) {
			render(ok, message, project);
		} else {
			render(project, storyId, cId, ok);
		}
	}


	/**
	 * @author Monayri
	 * Issue : 227
	 * Sprint : 4
	 */

	public static void magicShow(long projectId, long componentId, int reviewer, int assignee, long taskId, int tasks){
		String title;
		if (componentId != 0) {
			Component component = Component.findById(componentId);
			if(tasks==1){
				title="Component "+component.number+" Tasks";
			}else
				title="Component "+component.number+" Stories";
			
			render(component.componentStories, title);
		} else {
			if(taskId!=0){
				Task task1 = Task.findById(taskId);
				title ="S"+task1.taskStory.number+ " Task"+task1.number;
				render(task1, title);
			}else{
				if(reviewer==1){
					title="As reviewer";
					User user = Security.getConnected();
					Project project = Project.findById(projectId);
					Component component=null;
					for(Component comp: user.components){
						if(comp.project.equals(project)){
							component = comp;
						}
					}
					List<Task> task = new ArrayList<Task>();
					if(component!=null){
						for(Story story : component.componentStories){
							for(Task task2 : story.storiesTask){
								if(task2.reviewer.equals(user)){
									task.add(task2);
								}
							}
						}
						}
					System.out.println(task);
					render(task, title);
				}else{
					if(assignee==1){
						title="As assignee";
						User user = Security.getConnected();
						Project project = Project.findById(projectId);
						Component component=null;
						for(Component comp: user.components){
							if(comp.project.equals(project)){
								component = comp;
							}
						}
						List<Task> task = new ArrayList<Task>();
						if(component!=null){
							for(Story story : component.componentStories){
								for(Task task2 : story.storiesTask){
									if(task2.assignee.equals(user)){
										task.add(task2);
									}
								}
							}
							}
						
						render(task, title);
					}else{
						if(projectId!=0){
							title="Project Tasks";
							Project project = Project.findById(projectId);
							List<Story> stories = new ArrayList<Story>();
							for (Component component : project.components) {
								stories.addAll(component.componentStories);
							}
							render(stories, title);
						}
					}
				}
			}
		}
		 title="bla";
		render(title);
	}
	
	public static void viewStory(long storyId)
	{
		Story story = Story.findById(storyId);
		String[] success = story.succussSenario.split("/n");
		String[] failure = story.failureSenario.split("/n");
		render(story,success,failure);
	}

	/**
	 * Forbids access to listing all stories!
	 * 
	 * @author mahmoudsakr
	 */
	public static void list() {
		forbidden();
	}
}
