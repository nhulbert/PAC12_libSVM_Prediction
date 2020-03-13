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

	/**
	 * 
	 */
	private static final long serialVersionUID = -219917741270854261L;
	private static final Logger LOGGER = Logger.getLogger(IndexServlet.class.getName());

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		String team1Table = "";
		String team2Table = "";

		DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");

		try (Connection conn = pool.getConnection()) {
			// PreparedStatements are compiled by the database immediately and executed at a later date.
			// Most databases cache previously compiled queries, which improves efficiency.
			
			List<String[]> resultList = getTeams(conn);			

			team1Table = toTeamsHTML(resultList, "team1");
			team2Table = toTeamsHTML(resultList, "team2");
		} catch (SQLException ex) {			
			throw new ServletException("Unable to successfully connect to the database. Please check the "
					+ "steps in the README and try again.", ex);
		}

		req.setAttribute("team1", team1Table);
		req.setAttribute("team2", team2Table);
		req.getRequestDispatcher("/index.jsp").forward(req, resp);
	}

	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp)
			throws IOException, ServletException {

		String table = "";
		String team1Table = "";
		String team2Table = "";
		String resultStr = "";
		
		try {
			int rushYards = Integer.parseInt(req.getParameter("rushYards"));
			int passYards = Integer.parseInt(req.getParameter("passYards"));
			int tds = Integer.parseInt(req.getParameter("tds"));
			int tflYards = Integer.parseInt(req.getParameter("tflYards"));
			int sacks = Integer.parseInt(req.getParameter("sacks"));
			int inters = Integer.parseInt(req.getParameter("inters"));

			String team1 = req.getParameter("team1");
			String team2 = req.getParameter("team2");
			//double cVal = Double.parseDouble(req.getParameter("cVal"));
			
			DataSource pool = (DataSource) req.getServletContext().getAttribute("my-pool");

		  svm_problem prob = null;

		  svm_node node = null;

			try (Connection conn = pool.getConnection()) {
				Map<TeamYear, List<Double>> strengthMap =
						getTeamsStrengthMap(conn, rushYards, passYards, tds,
									  		tflYards, sacks, inters);
				TrainResults results = trainModel(0.00001, conn, strengthMap, rushYards, passYards, tds, tflYards, sacks, inters);
				String accuracy = "Training Validation Accuracy: " + results.validationAccuracy;
				
				String teamResultStr = "";
				List<String[]> resultList = getTeams(conn);			

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

		req.setAttribute("team1", team1Table);
		req.setAttribute("team2", team2Table);
		req.setAttribute("result", resultStr);
		
		req.getRequestDispatcher("/index.jsp").forward(req, resp);
	}
	
	private List<String[]> getTeams(Connection conn) throws SQLException {
		PreparedStatement teams =  conn.prepareStatement(
				"SELECT Tid, Tname, Uname FROM TEAM");
		// Execute the statement
		ResultSet results = teams.executeQuery();

		List<String[]> resultList = new ArrayList<>();

		while (results.next()) {
			String[] result = new String[3];
			for (int i=1; i<=3; i++) {
				result[i-1] = results.getString(i);
			}
			resultList.add(result);
		}
		
		return resultList;
	}
	
	private int predict(Map<TeamYear, List<Double>> strengthMap, svm_model model, String team1, String team2) {
		List<Double> strength1 = strengthMap.get(new TeamYear(team1, "2019"));
		List<Double> strength2 = strengthMap.get(new TeamYear(team2, "2019"));
		List<Double> gameFeatures = elWiseDiff(strength1, strength2);
		
		svm_node[] nodes = new svm_node[gameFeatures.size()];
		for (int i=0; i<gameFeatures.size(); i++) {
			svm_node node = new svm_node();
			node.index = i;
			node.value = gameFeatures.get(i);
			nodes[i] = node;
		}
		
		return (int)(libsvm.svm.svm_predict(model, nodes)+0.5);
	}
	
	private TrainResults trainModel(double cVal, Connection conn, Map<TeamYear, List<Double>> strengthMap, int rushYards, int passYards, int tds,
						    int tflYards, int sacks, int inters) throws SQLException {
		List<Game> games = getGames(conn);
		shuffleGames(games);
		
		svm_problem prob = new svm_problem();
		prob.l = (int)(games.size()*0.8);
		
		double[] y = new double[prob.l];
		svm_node[][] gameNodes = new svm_node[prob.l][];
		prob.y = y;
		prob.x = gameNodes;
		
		int offset = 0;
		for (int h=0; h<prob.l+offset; h++) {
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
				gameNodes[h-offset] = nodes;
				y[h] = game.result;
			} else {
				offset++;
				prob.l--;
			}
		}
		
		prob.y = Arrays.copyOf(prob.y, prob.l);
		prob.x = Arrays.copyOf(prob.x, prob.l);
		
		svm_parameter params = new svm_parameter();
		params.svm_type = svm_parameter.C_SVC;
		params.kernel_type = svm_parameter.LINEAR;
		params.cache_size = 1000;
		params.C = cVal;
		params.eps = 0.001;
		params.shrinking = 1;
		params.probability = 0;
		params.nr_weight = 0;

		String checkParamRes = svm_check_parameter(prob, params);
		svm_model model = null;
		double accuracy = 0d;
		
		if (checkParamRes == null) {
			model = svm_train(prob, params);
			
			int correct = 0;
			int total = 0;
			
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
			
			accuracy = correct/(double)total;
			System.out.println("BINARY PREDICTION ACCURACY: " + accuracy);
		}
		return new TrainResults(model, accuracy);
	}
	
	private List<Game> getGames(Connection conn) throws SQLException {
		String sqlStr = "select * from GAME";
		PreparedStatement players =  conn.prepareStatement(sqlStr);			
		ResultSet results = players.executeQuery();
		
		List<Game> out = new ArrayList<>();
		
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
	
	private Map<TeamYear, List<Double>> getTeamsStrengthMap(
			Connection conn,
			int rushYards,
			int passYards,
			int tds,
			int tflYards,
			int sacks,
			int inters) throws SQLException {
		
		Map<TeamYear, List<Double>> out = new HashMap<>();
		
		String sqlStr = "select Tid, SYear from TEAM, (select distinct(SYear) as SYear from GAME) as T";
		PreparedStatement players =  conn.prepareStatement(sqlStr);			
		ResultSet results = players.executeQuery();
		
		while (results.next()) {
			String teamID = results.getString(1);
			String year = results.getString(2);
			
			TeamYear teamYear = new TeamYear(teamID, year);
			List<Double> strengthVector =
				getTeamStrengthVector(conn, teamID, year,
									  rushYards, passYards, tds,
									  tflYards, sacks, inters);
			if (strengthVector != null) {
				out.put(teamYear, strengthVector);
			}
		}
		
		return out;
	}
	
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
		
		addSelect(selects, limits, limValues, rushYards, "RYards");
		addSelect(selects, limits, limValues, passYards, "PYards");
		addSelect(selects, limits, limValues, tds, "TD");
		addSelect(selects, limits, limValues, tflYards, "Tflyds");
		addSelect(selects, limits, limValues, sacks, "Sack");
		addSelect(selects, limits, limValues, inters, "Inter");
		
		StringBuilder sqlStr = new StringBuilder();
		for (int i=0; i<selects.size(); i++) {
			String selectStr = selects.get(i);
			String limitStr = limits.get(i);
			
			if (i != 0) {
				sqlStr.append(" UNION ALL ");
			}
			sqlStr.append("(select ").append(Integer.toString(i)).append(" as feature, ").append(selectStr)
				  .append(" from (PLAYER left join OFFPLAYER on PLAYER.Pid = OFFPLAYER.Pid) left join DEFPLAYER on PLAYER.Pid = DEFPLAYER.Pid")
				  .append(" where Tid = " + teamId + " and " + "SYear = ").append(year)
			      .append(" ")
				  .append(" ").append(limitStr).append(")");
		}
		sqlStr.append(" ORDER BY feature ASC, val DESC");
		
		PreparedStatement players =  conn.prepareStatement(sqlStr.toString());			
		ResultSet results = players.executeQuery();
		
		int prevInd = 0;
		int curCount = 0;
		int ind=-1;
		while (results.next()) {
			ind = results.getInt(1);
			String val = results.getString(2); // retrieve single statistic column
			if (ind != prevInd) {
				for (int i=curCount; i<limValues.get(ind-1); i++) {
					resultList.add(0d);
				}
				curCount = 0;
			}
			if (val == null) {
				resultList.add(0d); // pad feature vector with zeros if a team doesn't have enough players with the stat
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
		assert(testSum == resultList.size());
		
		return resultList;
	}
	
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
	
	private void addSelect(List<String> selects, List<String> limits, List<Integer> limValues, int count, String string) {
		if (count != -1) {
			String select = string;
			String limit = "";
			
			if (count == 0) {
				select = "SUM("+select+") as val";
				limValues.add(1);
			} else {
				select = select + " as val";
				limit = "limit " + count;
				limValues.add(count);
			}
			selects.add(select);
			limits.add(limit);
		}
	}
	
	private String toHTML(ResultSet results) throws SQLException {	    
		StringBuilder sb = new StringBuilder();

		sb.append("<table>\n");
		sb.append("<tr>\n");
		sb.append("<th>\n");
		sb.append("</th>\n");
		sb.append("<th>\n");
		sb.append("First Name");
		sb.append("</th>\n");
		sb.append("<th>\n");
		sb.append("Last Name");
		sb.append("</th>\n");
		sb.append("</tr>\n");
		
		while (results.next()) {
			sb.append("<tr>\n");
			sb.append("<td>\n");
			sb.append("</td>\n");
			sb.append("<td>\n");
			sb.append(results.getString(2));
			sb.append("</td>\n");
			sb.append("<td>\n");
			sb.append(results.getString(3));
			sb.append("</td>\n");
			sb.append("</tr>\n");
		}

		sb.append("</table><br>\n");

		return sb.toString();
	}
	
	private class Game {
		String teamID1;
		String teamID2;
		String year;
		int team1Score;
		int team2Score;
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
	
	private List<Double> elWiseDiff(List<Double> vec1, List<Double> vec2) {
		assert(vec1.size() == vec2.size());
		
		List<Double> out = new ArrayList<>(vec1.size());
		
		for (int i=0; i<vec1.size(); i++) {
			out.add(vec1.get(i) - vec2.get(i));
		}
		
		return out;
	}
	
	private void shuffleGames(List<Game> games) {
		Random rand = new Random();
		for (int i=0; i<games.size()-1; i++) {
			int ind = i + rand.nextInt(games.size() - i);
			Game temp = games.get(i);
			games.set(i, games.get(ind));
			games.set(ind, temp);
		}
	}
	
	private class TeamYear {
		String teamID;
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
	
	private class TrainResults {
		svm_model model;
		double validationAccuracy;
		
		public TrainResults(svm_model model, double validationAccuracy) {
			this.model = model;
			this.validationAccuracy = validationAccuracy;
		}
	}
}
