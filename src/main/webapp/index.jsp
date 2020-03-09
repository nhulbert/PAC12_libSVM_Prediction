<!--
Copyright 2018 Google LLC

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
-->
<%@ page contentType="text/html;charset=UTF-8" language="java" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<html lang="en">
<head>
    <title>PAC-12 DB</title>
    <h1>PAC-12 Prediction Utility</h1>
</head>

  <body>  
  <form method="post" action="" class="form" id="code">
  	  <h2>Data prediction is based on:</h2>
  	  Offense: </br>
  	  Rushing yards:
  	  <select id="rushYards" name="rushYards">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>
  	  
  	  Passing yards:
  	  <select id="passYards" name="passYards">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>
      
      Touchdowns:
	  <select id="tds" name="tds">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>

      </br>
      Defense: </br>
      Tackle Loss Yards
      <select id="tflYards" name="tflYards">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>
	  
	  Sacks
      <select id="sacks" name="sacks">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>
      
      Interceptions
      <select id="inters" name="inters">
	    <option value="-1">Not used</option>
	    <option value="0">Team Total</option>
	    <option value="1">Top player</option>
	    <option value="2">Top 2 players</option>
	    <option value="3">Top 3 players</option>
	    <option value="4">Top 4 players</option>
	    <option value="5">Top 5 players</option>
	    <option value="6">Top 6 players</option>
	    <option value="7">Top 7 players</option>
	    <option value="8">Top 8 players</option>
	    <option value="9">Top 9 players</option>
	    <option value="10">Top 10 players</option>	   
	  </select>
	  </br>

  	  </br></br></br>
  	  <h2>Matchup:</h2>
  	  Predict outcome of ${team1} vs. ${team2}:
  	  </br>
  	  <input type="submit" value="Go">
  </form>
  ${result}
  
  </body>
</html>
