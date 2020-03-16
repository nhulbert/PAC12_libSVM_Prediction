/*
 * Neil Hulbert
 * TCSS 445
 */
package com.example.cloudsql;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import static libsvm.svm.*;
import libsvm.*;

@WebServlet(name = "Index", value = "")
public class IndexServlet extends HttpServlet {

	private static final long serialVersionUID = -219917741270854261L;
	private static final Logger LOGGER = Logger.getLogger(IndexServlet.class.getName());

	/**
	 * The method called when a client makes a GET request
	 */
	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		String team1Table = "";
		String team2Table = "";
		
		// get the SQL connection pool
		DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");

		try (Connection conn = pool.getConnection()) {
			// query the DB for the list of team to display in a drop-down
			List<String[]> resultList = getTeams(conn);			

			team1Table = toTeamsHTML(resultList, "team1");
			team2Table = toTeamsHTML(resultList, "team2");
		} catch (SQLException ex) {			
			throw new ServletException("Unable to successfully connect to the database. Please check the "
					+ "steps in the README and try again.", ex);
		}

		// set the variables to be used by the JSP page
		req.setAttribute("team1", team1Table);
		req.setAttribute("team2", team2Table);
		req.getRequestDispatcher("/index.jsp").forward(req, resp);
	}

	/**
	 * The method called when the client makes a POST request
	 */
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		String table = "";
		String team1Table = "";
		String team2Table = "";
		String resultStr = "";
		
		try {
			// read what type of stats to use from the drop-down menus
			int rushYards = Integer.parseInt(req.getParameter("rushYards"));
			int passYards = Integer.parseInt(req.getParameter("passYards"));
			int tds = Integer.parseInt(req.getParameter("tds"));
			int tflYards = Integer.parseInt(req.getParameter("tflYards"));
			int sacks = Integer.parseInt(req.getParameter("sacks"));
			int inters = Integer.parseInt(req.getParameter("inters"));

			String team1 = req.getParameter("team1");
			String team2 = req.getParameter("team2");
			//double cVal = Double.parseDouble(req.getParameter("cVal"));
			
			// get the SQL connection pool
			DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");

			// the libSVM variables used for modeling
			svm_problem prob = null;
		    svm_node node = null;
		    	
			try (Connection conn = pool.getConnection()) {
				// query the DB for the strength feature vector for each team
				Map<TeamYear, List<Double>> strengthMap =
						getTeamsStrengthMap(conn, rushYards, passYards, tds,
									  		tflYards, sacks, inters);
				// trains a libSVM model using the feature vectors
				TrainResults results = trainModel(conn, strengthMap, rushYards, passYards, tds, tflYards, sacks, inters);
				String accuracy = "Training Validation Accuracy: " + results.validationAccuracy;
				
				String teamResultStr = "";
				List<String[]> resultList = getTeams(conn);			

				// make the prediction using the model
				int prediction = predict(strengthMap, results.model, team1, team2);
				if (prediction == 1) {
					teamResultStr = resultList.get(Integer.parseInt(team1))[2]
									+ " beats "
									+ resultList.get(Integer.parseInt(team2))[2];
				} else {
					teamResultStr = resultList.get(Integer.parseInt(team2))[2]
							+ " beats "
							+ resultList.get(Integer.parseInt(team1))[2];
				}
				
				resultStr = "<h2>Results:</h2>" + accuracy + "</br>" + teamResultStr;
				
				team1Table = toTeamsHTML(resultList, "team1");
				team2Table = toTeamsHTML(resultList, "team2");
				
				// PreparedStatements are compiled by the database immediately and executed at a later date.
				// Most databases cache previously compiled queries, which improves efficiency.
				
				// Execute the statement
			} catch (SQLException ex) {req.setAttribute("predict", table);
				throw new ServletException("Unable to successfully connect to the database. Please check the "
						+ "steps in the README and try again.", ex);
			}
		} catch (NumberFormatException nfe) {

		}

		// set the attributes to be used by the JSP page
		req.setAttribute("team1", team1Table);
		req.setAttribute("team2", team2Table);
		req.setAttribute("result", resultStr);
		
		req.getRequestDispatcher("/index.jsp").forward(req, resp);
	}
	
	/**
	 * Retrieves the list of teams from the database
	 * 
	 * @param conn the connection variable for the SQL database
	 * @return a list of string arrays that hold the attributes of each team
	 * @throws SQLException
	 */
	private List<String[]> getTeams(Connection conn) throws SQLException {
		PreparedStatement teams =  conn.prepareStatement(
				"SELECT Tid, Tname, Uname FROM TEAM");
		// Execute the statement
		ResultSet results = teams.executeQuery();

		List<String[]> resultList = new ArrayList<>();

		// convert the database result into the necessary form
		while (results.next()) {
			String[] result = new String[3];
			for (int i=1; i<=3; i++) {
				result[i-1] = results.getString(i);
			}
			resultList.add(result);
		}
		
		return resultList;
	}
	
	/**
	 * Predicts the result of a 2019 game using the list of strength vectors for each team
	 * and a trained libSVM model
	 * 
	 * @param strengthMap a map holding the strength feature vectors for each team/year combination
	 * @param model the libSVM model used to predict
	 * @param team1 the id of the first team
	 * @param team2 the id of the second team
	 * @return a 1 for a team1 win, or a 0 for a team2 win
	 */
	private int predict(Map<TeamYear, List<Double>> strengthMap, svm_model model, String team1, String team2) {
		List<Double> strength1 = strengthMap.get(new TeamYear(team1, "2019"));
		List<Double> strength2 = strengthMap.get(new TeamYear(team2, "2019"));
		
		// produce the feature vector for the match by subtracting the strength vectors
		List<Double> gameFeatures = elWiseDiff(strength1, strength2);
		
		// iterate over all the vector elements and copy them into a libSVM node
		svm_node[] nodes = new svm_node[gameFeatures.size()];
		for (int i=0; i<gameFeatures.size(); i++) {
			svm_node node = new svm_node();
			node.index = i;
			node.value = gameFeatures.get(i);
			nodes[i] = node;
		}
		
		// round the predicted value to 1 (for a team 1 win) or 0 (for a team 2 win)
		return (int)(libsvm.svm.svm_predict(model, nodes)+0.5);
	}
	
	/**
	 * Trains the libSVM model using the past games played in the database
	 * 
	 * @param conn the SQL connection
	 * @param strengthMap the map holding the strength feature vectors for each team
	 * @param rushYards what kind of rushYard features to use
	 * @param passYards what kind of passYard features to use
	 * @param tds what kind of touchdown features to use
	 * @param tflYards what kind of tackles for loss features to use
	 * @param sacks what kind of number of sacks features to use
	 * @param inters what kind of interceptions made features to use
	 * @return a TrainResults object holding the libSVM model and the validation accuracy
	 * that model (~80/20 split)
	 * @throws SQLException
	 */
	private TrainResults trainModel(Connection conn, Map<TeamYear, List<Double>> strengthMap, int rushYards, int passYards, int tds,
						    int tflYards, int sacks, int inters) throws SQLException {
		List<Game> games = getGames(conn);
		// shuffle the retrieved games to eliminate training bias
		shuffleGames(games);
		
		// initialize the libSVM problem
		svm_problem prob = new svm_problem();
		// set the training/validation split
		prob.l = (int)(games.size()*0.8);
		
		double[] y = new double[prob.l];
		svm_node[][] gameNodes = new svm_node[prob.l][];
		prob.y = y;
		prob.x = gameNodes;
		
		// iterate through each game and train using the difference of the team strength
		// vectors as the feature vectors for each match
		int offset = 0;
		for (int h=0; h<prob.l+offset; h++) {
			Game game = games.get(h);
			List<Double> strength1 = strengthMap.get(new TeamYear(game.teamID1, game.year));
			List<Double> strength2 = strengthMap.get(new TeamYear(game.teamID2, game.year));
			// if either strength vector cannot be found, abort and don't add
			if (strength1 != null && strength2 != null) {
				List<Double> gameFeatures = elWiseDiff(strength1, strength2);
				
				svm_node[] nodes = new svm_node[gameFeatures.size()];
				for (int i=0; i<gameFeatures.size(); i++) {
					svm_node node = new svm_node();
					node.index = i;
					node.value = gameFeatures.get(i);
					nodes[i] = node;
				}
				gameNodes[h-offset] = nodes;
				y[h] = game.result;
			} else {
				offset++;
				prob.l--;
			}
		}
		
		// resize prob arrays to only hold required number of elements, prob.l
		// apparently has no effect
		prob.y = Arrays.copyOf(prob.y, prob.l);
		prob.x = Arrays.copyOf(prob.x, prob.l);
		
		// set the libSVM model parameters
		svm_parameter params = new svm_parameter();
		params.svm_type = svm_parameter.C_SVC;
		params.kernel_type = svm_parameter.LINEAR;
		params.cache_size = 1000;
		params.C = 100;
		params.eps = 0.001;
		params.shrinking = 1;
		params.probability = 0;
		params.nr_weight = 0;

		// check that the libSVM model parameters are valid
		String checkParamRes = svm_check_parameter(prob, params);
		svm_model model = null;
		double accuracy = 0d;
		
		if (checkParamRes == null) {
			// do the actual training
			model = svm_train(prob, params);
			
			int correct = 0;
			int total = 0;
			
			// validate the resulting model using the 20% of games set aside for validation
			for (int h=prob.l+offset; h<games.size(); h++) {
				Game game = games.get(h);
				List<Double> strength1 = strengthMap.get(new TeamYear(game.teamID1, game.year));
				List<Double> strength2 = strengthMap.get(new TeamYear(game.teamID2, game.year));
				if (strength1 != null && strength2 != null) {
					List<Double> gameFeatures = elWiseDiff(strength1, strength2);
					
					svm_node[] nodes = new svm_node[gameFeatures.size()];
					for (int i=0; i<gameFeatures.size(); i++) {
						svm_node node = new svm_node();
						node.index = i;
						node.value = gameFeatures.get(i);
						nodes[i] = node;
					}
					
					double pred = libsvm.svm.svm_predict(model, nodes);
									
					int res = (int)(pred + 0.5);
					
					if (res == game.result) {
						correct++;
					}
					total++;
				}
			}
			
			// calculate validation accuracy and print to the console
			accuracy = correct/(double)total;
			System.out.println("BINARY PREDICTION ACCURACY: " + accuracy);
		}
		return new TrainResults(model, accuracy);
	}
	
	/**
	 * Retrieves all the games present in a database
	 * 
	 * @param conn the connection object for the DB
	 * @return the list of all the games present in the database
	 * @throws SQLException
	 */
	private List<Game> getGames(Connection conn) throws SQLException {
		String sqlStr = "select * from GAME";
		PreparedStatement players =  conn.prepareStatement(sqlStr);			
		ResultSet results = players.executeQuery();
		
		List<Game> out = new ArrayList<>();
		
		// randomize team order to minimize bias in training
		Random teamOrderRandomizer = new Random();
		
		while (results.next()) {
			String teamID1 = results.getString(2);
			String teamID2 = results.getString(3);
			String year = results.getString(7);
			int team1score = results.getInt(4);
			int team2score = results.getInt(5);
			int result = (team1score > team2score) ? 1 : 0;
			
			out.add(new Game(teamOrderRandomizer, teamID1, teamID2, year, team1score, team2score, result));
		}
		
		return out;
	}
	
	/**
	 * Maps a team/year combination to a strength vector that indicates a number of 
	 * possibly aggregate player stats and represents the strength of each team
	 * 
	 * @param conn the connection object to the DB
	 * @param rushYards the rushYards features to use
	 * @param passYards the passYards features to use
	 * @param tds the touchdown features to use
	 * @param tflYards the tackles for loss features to use
	 * @param sacks the number of sacks features to use
	 * @param inters the number of interceptions features to use
	 * @return the team/year to strength vector map
	 * @throws SQLException
	 */
	private Map<TeamYear, List<Double>> getTeamsStrengthMap(
			Connection conn,
			int rushYards,
			int passYards,
			int tds,
			int tflYards,
			int sacks,
			int inters) throws SQLException {
		
		Map<TeamYear, List<Double>> out = new HashMap<>();
		
		// select each team/year combo
		String sqlStr = "select Tid, SYear from TEAM, (select distinct(SYear) as SYear from GAME) as T";
		PreparedStatement players =  conn.prepareStatement(sqlStr);			
		ResultSet results = players.executeQuery();
		
		// assemble the strength vectors for each
		int vecLength = 0;
		while (results.next()) {
			String teamID = results.getString(1);
			String year = results.getString(2);
			
			TeamYear teamYear = new TeamYear(teamID, year);
			List<Double> strengthVector =
				getTeamStrengthVector(conn, teamID, year,
									  rushYards, passYards, tds,
									  tflYards, sacks, inters);
			if (strengthVector != null) {
				vecLength = Math.max(vecLength, strengthVector.size());
				out.put(teamYear, strengthVector);
			}
		}
		
		// scale each vector element to aid training
		List<Double> mins = new ArrayList<>(vecLength); // scale the values to aid training
		List<Double> maxes = new ArrayList<>(vecLength);
		for (int i=0; i<vecLength; i++) {
			mins.add(Double.POSITIVE_INFINITY);
			maxes.add(Double.NEGATIVE_INFINITY);
		}
		
		for (List<Double> vec : out.values()) {
			for (int i=0; i<vecLength; i++) {
				mins.set(i, Math.min(mins.get(i), vec.get(i)));
				maxes.set(i, Math.max(maxes.get(i), vec.get(i)));
			}
		}
		for (List<Double> vec : out.values()) {
			for (int i=0; i<vecLength; i++) {
				double denom = (maxes.get(i) - mins.get(i));
				// don't divide by zero if a vector element number has a range of zero
				if (denom == 0.0) {
					vec.set(i, 0.0);
				} else {
					vec.set(i, (vec.get(i)-mins.get(i)) / denom);
				}
			}
		}
		
		return out;
	}
	
	/**
	 * Produces a strength vector that represents the strength of a team/year combo using
	 * a number of possibly aggregate player stats
	 * 
	 * @param conn the SQL DB connection
	 * @param teamId the id of the team
	 * @param year the year of the team
	 * @param rushYards what kind of rushYards features to use
	 * @param passYards what kind of passYards features to use
	 * @param tds what kind of touchdowns features to use
	 * @param tflYards what kind of tackle for loss features to use
	 * @param sacks what kind of sacks features to use
	 * @param inters what kind of interceptions features to use
	 * @return a list of Doubles that should represent the strength of a team in a year
	 * @throws SQLException
	 */
	private List<Double> getTeamStrengthVector(Connection conn,
								   String teamId,
								   String year,
								   int rushYards,
								   int passYards,
								   int tds,
								   int tflYards,
								   int sacks,
								   int inters) throws SQLException {
		List<String> selects = new ArrayList<>();
		List<String> limits = new ArrayList<>();
		List<Integer> limValues = new ArrayList<>();
		
		List<Double> resultList = new ArrayList<>();
		
		// prepare the individual parts of the SQL statement
		addSelect(selects, limits, limValues, rushYards, "RYards");
		addSelect(selects, limits, limValues, passYards, "PYards");
		addSelect(selects, limits, limValues, tds, "TD");
		addSelect(selects, limits, limValues, tflYards, "Tflyds");
		addSelect(selects, limits, limValues, sacks, "Sack");
		addSelect(selects, limits, limValues, inters, "Inter");
		
		// assemble the SQL query
		StringBuilder sqlStr = new StringBuilder();
		for (int i=0; i<selects.size(); i++) {
			String selectStr = selects.get(i);
			String limitStr = limits.get(i);
			
			if (i != 0) {
				sqlStr.append(" UNION ALL ");
			}
			sqlStr.append("(select ").append(Integer.toString(i)).append(" as feature, ").append(selectStr)
				  // need to join player with both defensive and offensive stats to consider all used stats
				  .append(" from (PLAYER left join OFFPLAYER on PLAYER.Pid = OFFPLAYER.Pid) left join DEFPLAYER on PLAYER.Pid = DEFPLAYER.Pid")
				  .append(" where Tid = " + teamId + " and " + "SYear = ").append(year)
			      .append(" ")
				  .append(" ").append(limitStr).append(")");
		}
		// ordering makes sure the vector is in a consistent order
		sqlStr.append(" ORDER BY feature ASC, val DESC");
		
		PreparedStatement players =  conn.prepareStatement(sqlStr.toString());			
		ResultSet results = players.executeQuery();
		
		// put the query results into a List
		int prevInd = 0;
		int curCount = 0;
		int ind=-1;
		while (results.next()) {
			ind = results.getInt(1);
			String val = results.getString(2); // retrieve single statistic column
			if (ind != prevInd) {
				for (int i=curCount; i<limValues.get(ind-1); i++) {
					resultList.add(0d); // pad feature vector with zeros if a team doesn't have enough players with the stat
				}
				curCount = 0;
			}
			if (val == null) {
				resultList.add(0d);
			} else {
				resultList.add(Double.parseDouble(val));
			}
			curCount++;
			prevInd = ind;
		}
		if (ind == -1) {
			return null;
		}
		for (int i=curCount; i<limValues.get(ind); i++) {
			resultList.add(0d);
		}
		
		int testSum = 0;
		for (int i=0; i<limValues.size(); i++) {
			testSum += limValues.get(i);
		}
		// don't return any vector if data was missing from the DB
		if (testSum != resultList.size()) {
			return null;
		}
		
		return resultList;
	}
	
	/**
	 * Converts a list of teams into a dropdown menu
	 * 
	 * @param resultList the list of teams
	 * @param valueName the id to use for the team
	 * @return an HTML dropdown menu string
	 */
	private String toTeamsHTML(List<String[]> resultList, String valueName) {
		// In format Tid, Tname, Uname

		StringBuilder sb = new StringBuilder();
		sb.append("<select id=\""+valueName+"\" name=\""+valueName+"\">\n");
		for (String[] result : resultList) {
			String fullTeamName = result[2] + " " + result[1];
			sb.append("<option value=\""+result[0]+"\">"+fullTeamName+"</option>\n");
		}
		sb.append("</select>\n");

		return sb.toString();
	}
	
	/**
	 * Constructs the individual parts of a query retrieving the strength vector of a team
	 * 
	 * @param selects a list of the select clauses
	 * @param limits a list of the order by and limit clauses
	 * @param limValues a list of Java integers holding the number of features for each statistic
	 * @param count the feature type for the statistic type (-1 for unused)
	 * @param string the name of the statistic to select features for
	 */
	private void addSelect(List<String> selects, List<String> limits, List<Integer> limValues, int count, String string) {
		if (count != -1) {
			String select = string;
			String limit = "";
			
			if (count == 0) {
				select = "SUM("+select+") as val";
				limValues.add(1);
			} else {
				select = select + " as val";
				limit = "order by val desc limit " + count;
				limValues.add(count);
			}
			selects.add(select);
			limits.add(limit);
		}
	}
	
	/**
	 * A class that holds the attributes of a pac12 football game.
	 * @author neil
	 *
	 */
	private class Game {
		/**
		 * the ID for team1
		 */
		String teamID1;
		/**
		 * the ID for team2
		 */
		String teamID2;
		/**
		 * the season year the game was played in
		 */
		String year;
		/**
		 * the score team1 finished with
		 */
		int team1Score;
		/**
		 * the score team2 finished with
		 */
		int team2Score;
		/**
		 * 1 if team1 won, 0 if team2 won
		 */
		int result;
		
		public Game(Random rand,
					String teamID1,
					String teamID2,
					String year,
					int team1Score,
					int team2Score,
					int result) {
			if (rand.nextBoolean()) {
				this.teamID1 = teamID1;
				this.teamID2 = teamID2;
				this.team1Score = team1Score;
				this.team2Score = team2Score;
				this.result = result;
			} else {
				this.teamID1 = teamID2;
				this.teamID2 = teamID1;
				this.team1Score = team2Score;
				this.team2Score = team1Score;
				this.result = 1-result;
			}
			this.year = year;
		}
	}
	
	/**
	 * Computes the element-wise difference between two double vectors
	 * 
	 * @param vec1 the double vector from which to subtract
	 * @param vec2 the double vector to subtract
	 * @return a list representing the resulting difference vector
	 */
	private List<Double> elWiseDiff(List<Double> vec1, List<Double> vec2) {		
		List<Double> out = new ArrayList<>(vec1.size());
		
		for (int i=0; i<vec1.size(); i++) {
			out.add(vec1.get(i) - vec2.get(i));
		}
		
		return out;
	}
	
	/**
	 * Shuffles the provided list of games pseudorandomly
	 * @param games
	 */
	private void shuffleGames(List<Game> games) {
		Random rand = new Random();
		for (int i=0; i<games.size()-1; i++) {
			int ind = i + rand.nextInt(games.size() - i);
			Game temp = games.get(i);
			games.set(i, games.get(ind));
			games.set(ind, temp);
		}
	}
	
	/**
	 * A class representing a team in some year.
	 * @author neil
	 *
	 */
	private class TeamYear {
		/**
		 * The ID of the team
		 */
		String teamID;
		/**
		 * The season year the team played
		 */
		String year;
		
		public TeamYear(String teamID, String year) {
			this.teamID = teamID;
			this.year = year;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((teamID == null) ? 0 : teamID.hashCode());
			result = prime * result + ((year == null) ? 0 : year.hashCode());
			return result;
		}
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			TeamYear other = (TeamYear) obj;
			if (teamID == null) {
				if (other.teamID != null)
					return false;
			} else if (!teamID.equals(other.teamID))
				return false;
			if (year == null) {
				if (other.year != null)
					return false;
			} else if (!year.equals(other.year))
				return false;
			return true;
		}
		
		@Override
		public String toString(){
			return teamID + ", " + year;
		}
	}
	
	/**
	 * An object used to hold the results of model training
	 * @author neil
	 */
	private class TrainResults {
		/**
		 * The trained model
		 */
		svm_model model;
		/**
		 * The validation accuracy of the trained model
		 */
		double validationAccuracy;
		
		public TrainResults(svm_model model, double validationAccuracy) {
			this.model = model;
			this.validationAccuracy = validationAccuracy;
		}
	}
}
