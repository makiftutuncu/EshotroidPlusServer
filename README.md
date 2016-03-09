Eshotroid+ Server
=================
Welcome to Eshotroid+ Server!

This is a server application providing information about public buses in İzmir, Turkey which are governed by [**Eshot**](http://www.eshot.gov.tr). It's purpose is to serve this information in Json format via a very simple RESTful API. There *will be* an Android application communicating with this server at [**Eshotroid+ Android**](https://github.com/mehmetakiftutuncu/EshotroidPlusAndroid).

Eshotroid+ Server will run at [**Eshotroid+ Server on Heroku**](https://eshotroidplusserver.herokuapp.com).

Disclaimer
----------
This application is developed as a hobby and **means absolutely no harm to Eshot**. It only serves information that is already available on [**Eshot's website**](http://www.eshot.gov.tr) in a format that is easier for other applications to use.

Technical Details
-----------------
Eshotroid+ Server application is developed using [**Play Framework**](https://www.playframework.com/) and [**Scala**](http://www.scala-lang.org/). It runs on [**Heroku**](https://www.heroku.com) and has a simple [**MySQL**](https://www.mysql.com) database for data persistence. It utilizes [**WS**](https://www.playframework.com/documentation/2.5.x/ScalaWS) for making HTTP requests.

API Reference
-------------
All requests use HTTP `GET` method. All responses will be `HTTP 200 OK` with `Content-Type: application/json`, including errors.

An example success response:

```javascript
{
  "success": /* object or array */
}
```

An example error response:

```javascript
{
  "errors": [/* one or more errors */]
}
```

Bus List API `/bus/list`
------------------------
This API returns the list of buses available.

```javascript
{
  "success": [
    {
      "id": 5,
      "departure": "Narlıdere",
      "arrival": "Üçkuyular İskele"
    }
    /* more objects */
  ]
}
```

Key       | Details
--------- | ---------------
id        | Id of the bus which is the bus number
departure | Name of the location from which bus' route starts
arrival   | Name of the location from which bus' route end

Bus Details API `/bus/{id}`
---------------
This API returns all the information about bus whose id is `{id}`.

```javascript
{
  "success": {
    "id": 169,
    "departure": "Balçova",
    "arrival": "Konak",
    "times": {
      "weekDays": {
        "departure": ["06:00" /* , more values */],
        "arrival": ["06:10" /* , more values */]
      },
      "saturday": {
        "departure": ["06:00" /* , more values */],
        "arrival": ["06:15" /* , more values */]
      },
      "sunday": {
        "departure": ["06:00" /* , more values */],
        "arrival": ["06:40" /* , more values */]
      }
    },
    "stops": {
      "departure": [
        {
          "id": 50176,
          "name": "Balçova Son Durak",
          "location": {"lat": 38.38914, "lon": 27.03775}
        }
        /* more objects */
      ],
      "arrival": [
        {
          "id": 10019,
          "name": "Bahribaba",
          "location": {"lat": 38.415519, "lon": 27.127131}
        }
        /* more objects */
      ]
    },
    "route": {
      "departure": [
        {"lat": 38.38443, "lon": 27.058214}
        /* more objects */
      ],
      "arrival": [
        {"lat": 38.384633, "lon": 27.058188}
        /* more objects */
      ]
    }
  }
}
```

Key                       | Details
------------------------- | ---------------------------------------------------------------
id                        | Id of the bus which is the bus number
departure                 | Name of the location from which bus' route starts
arrival                   | Name of the location from which bus' route end
times / `day` / departure | Times when bus leaves from departure location for `day`
times / `day` / arrival   | Times when bus leaves from arrival location for `day`
stops / departure         | Stops on the route from departure location to arrival location
stops / arrival           | Stops on the route from arrival location to departure location
route / departure         | Points on the route from departure location to arrival location
route / arrival           | Points on the route from arrival location to departure location

For a stop:

Key      | Details
-------- | --------------------
id       | Id of the stop
name     | Name of the stop
location | Location of the stop

For a location:

Key | Details
----| -------------------------
lat | Latitude of the location
lon | Longitude of the location

API Errors
----------
Eshotroid+ Server uses [**Errors**](https://github.com/mehmetakiftutuncu/Errors) to handle errors on server side. When something goes wrong, there will be a one or more errors.

Here is an example error response:

```json
{
  "errors": [
    {
      "name": "invalidData",
      "reason": "Bus id must be > 0!",
      "data": "-1"
    }
  ]
}
```

Key                 | Details
------------------- | ---------------
name                | Name of the error
reason *(Optional)* | Reason why the error occurred
data *(Optional)*   | Data related to the error

License
--------------
Eshotroid+ Server is licensed under the terms of the GNU General Public License version 3.

```
Eshotroid+ Server
Copyright (C) 2016  Mehmet Akif Tütüncü

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```