import './App.css'
import kona from "kona-js"
import {useState} from "react"

function App() {
	const [message, setMessage] = useState("");
	return (
		<>
			<p>Message: {message}</p>

			<button onClick={async () => {
				const response = await kona.call("test", "test", {
					message: "Hello from React!"
				});

				setMessage(response.response);
			}}>Get data from Kona Backend
			</button>
		</>
	)
}

export default App
