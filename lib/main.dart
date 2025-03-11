import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:shared_preferences/shared_preferences.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Wer-U Today?',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const LoginScreen(),
    );
  }
}

class LoginScreen extends StatefulWidget {
  const LoginScreen({Key? key}) : super(key: key);

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final TextEditingController _groupNameController = TextEditingController();
  final TextEditingController _userNameController = TextEditingController();
  final _formKey = GlobalKey<FormState>();
  bool _isLoading = false;
  
  // Method channel to communicate with native Kotlin code
  static const platform = MethodChannel('com.elisheato.tracker/location');

  @override
  void initState() {
    super.initState();
    _checkExistingUser();
  }

  Future<void> _checkExistingUser() async {
    setState(() => _isLoading = true);
    
    final prefs = await SharedPreferences.getInstance();
    final groupName = prefs.getString('groupName');
    final userName = prefs.getString('userName');
    
    if (groupName != null && userName != null) {
      _startLocationService(groupName, userName);
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (context) => HomeScreen(groupName: groupName, userName: userName)),
      );
    }
    
    setState(() => _isLoading = false);
  }
  
  Future<void> _startLocationService(String groupName, String userName) async {
    try {
      await platform.invokeMethod('startLocationService', {
        'groupName': groupName,
        'userName': userName
      });
    } on PlatformException catch (e) {
      debugPrint("Failed to start location service: ${e.message}");
    }
  }

  Future<void> _handleLogin() async {
    if (!_formKey.currentState!.validate()) return;
    
    setState(() => _isLoading = true);
    
    final groupName = _groupNameController.text.trim();
    final userName = _userNameController.text.trim();
    
    // Save user data to SharedPreferences
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('groupName', groupName);
    await prefs.setString('userName', userName);
    
    // Start location service
    await _startLocationService(groupName, userName);
    
    setState(() => _isLoading = false);
    
    if (mounted) {
      Navigator.pushReplacement(
        context,
        MaterialPageRoute(builder: (context) => HomeScreen(groupName: groupName, userName: userName)),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Location Tracker Login'),
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : Padding(
              padding: const EdgeInsets.all(16.0),
              child: Form(
                key: _formKey,
                child: Column(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    TextFormField(
                      controller: _groupNameController,
                      decoration: const InputDecoration(
                        labelText: 'Group Name',
                        hintText: 'Enter group name (used as DB name)',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return 'Please enter a group name';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 16),
                    TextFormField(
                      controller: _userNameController,
                      decoration: const InputDecoration(
                        labelText: 'User Name',
                        hintText: 'Enter your name (used as collection name)',
                        border: OutlineInputBorder(),
                      ),
                      validator: (value) {
                        if (value == null || value.isEmpty) {
                          return 'Please enter a user name';
                        }
                        return null;
                      },
                    ),
                    const SizedBox(height: 24),
                    ElevatedButton(
                      onPressed: _handleLogin,
                      style: ElevatedButton.styleFrom(
                        minimumSize: const Size(double.infinity, 50),
                      ),
                      child: const Text('Start Tracking'),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class HomeScreen extends StatefulWidget {
  final String groupName;
  final String userName;

  const HomeScreen({
    Key? key,
    required this.groupName,
    required this.userName,
  }) : super(key: key);

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  static const platform = MethodChannel('com.elisheato.tracker/location');
  String _status = "Tracking in background...";
  
  Future<void> _stopTracking() async {
    try {
      await platform.invokeMethod('stopLocationService');
      
      // Clear saved preferences
      final prefs = await SharedPreferences.getInstance();
      await prefs.clear();
      
      if (mounted) {
        Navigator.pushReplacement(
          context,
          MaterialPageRoute(builder: (context) => const LoginScreen()),
        );
      }
    } on PlatformException catch (e) {
      setState(() {
        _status = "Failed to stop tracking: ${e.message}";
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Location Tracking'),
        automaticallyImplyLeading: false,
      ),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(16.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Group: ${widget.groupName}',
                style: Theme.of(context).textTheme.headline6,
              ),
              const SizedBox(height: 8),
              Text(
                'User: ${widget.userName}',
                style: Theme.of(context).textTheme.headline6,
              ),
              const SizedBox(height: 24),
              const Icon(
                Icons.location_on,
                size: 64,
                color: Colors.blue,
              ),
              const SizedBox(height: 16),
              Text(
                _status,
                style: Theme.of(context).textTheme.subtitle1,
              ),
              const SizedBox(height: 32),
              ElevatedButton(
                onPressed: _stopTracking,
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.red,
                  minimumSize: const Size(double.infinity, 50),
                ),
                child: const Text('Stop Tracking'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
