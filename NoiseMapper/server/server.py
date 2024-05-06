from flask import Flask,request, send_from_directory
import sqlite3
from time import time
app = Flask(__name__)
conn = sqlite3.connect('db/mapdata.db', check_same_thread=False)
c = conn.cursor()

# Create a table if it doesn't exist
c.execute('''CREATE TABLE IF NOT EXISTS noise_measurements
             (timestamp INTEGER NOT NULL, room TEXT, noise REAL)''')
conn.commit()

@app.route('/measurements', methods=['POST'])
def save_request():
    data = request.json
    if data:
        # Save the request data to the database
        c.execute("INSERT INTO noise_measurements (timestamp, room, noise) VALUES (?,?,?)", (int(time()), data['room'], data['noise']))
        conn.commit()
        return 'Request saved successfully'
    else:
        return 'No data provided'

@app.route('/measurements', methods=['GET'])
def get_requests():
    # get the the start and end timestamps from the query parameters
    start_from = request.args.get('start_from')
    end_to = request.args.get('end_to')
    if not start_from or not end_to:
        start_from = str(time() - 3600*24*7)
        end_to = str(time())
    # if the query parameters are malformed, set the default values
    if not start_from.isdigit() or not end_to.isdigit():
        start_from = int(time()) - 3600*24*7
        end_to = int(time())
    if int(start_from) > int(end_to) or int(end_to) > int(time()) or int(start_from) < 0:
        start_from = int(time()) - 3600*24*7
        end_to = int(time())
        
    start_from = int(start_from)
    end_to = int(end_to)
    c.execute("SELECT * FROM noise_measurements WHERE timestamp >= ? AND timestamp <= ?", (start_from, end_to))
    rows = c.fetchall()
    return rows

@app.route('/resources/<filename>')
def download_file(filename):
    try:
        return send_from_directory('resources', filename)
    except Exception as e:
        return e
if __name__ == '__main__':
    app.run(host='0.0.0.0',port=5002)